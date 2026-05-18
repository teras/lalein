package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive coverage of auto-derive printf-scanning edge cases:
 * numeric vs non-numeric conversions, escape sequences (%%, %n), flag/width/
 * precision modifiers, positional refs in the handler, multi-form agreement,
 * and round-trip preservation of the inferred index.
 */
class AutoIndexEdgeCasesTest {

    private static Lalein build(String handler, Map<String, Object> form) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(handler, form);
        return MapLalein.fromMap(data);
    }

    private static Map<String, Object> form(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
        return m;
    }

    // ── Conversion-type coverage ─────────────────────────────────────────────

    @Test
    void numeric_float_conversion_isDriver() {
        String handler = "Saved %s for %f hours";
        Lalein l = build(handler, form(
                "o",  "%1$s for one hour (%2$.2f)",
                "r",  "%1$s for %2$.2f hours"));
        assertEquals("Alice for one hour (1.00)", l.format(handler, "Alice", 1.0));
        assertEquals("Alice for 2.50 hours",      l.format(handler, "Alice", 2.5));
    }

    @Test
    void numeric_hexAndOctal_areDriver() {
        // %x and %o are numeric conversions per java.util.Formatter
        String handler = "Code %s value %x";
        Lalein l = build(handler, form(
                "o", "%1$s value %2$x (one)",
                "r", "%1$s value %2$x"));
        assertEquals("A value 1 (one)", l.format(handler, "A", 1));
        assertEquals("A value ff",      l.format(handler, "A", 255));
    }

    @Test
    void nonNumeric_string_neverDrivesPlural() {
        // %s placeholders must never be considered as plural drivers.
        String handler = "Hello %s and %s";
        Lalein l = build(handler, form(
                "o", "Hello %1$s and one %2$s",
                "r", "Hello %1$s and many %2$s"));
        // No numeric placeholder anywhere → fallback to argumentIndex 1.
        // But arg1 is a String, so the call should error out clearly.
        LaleinException ex = assertThrows(LaleinException.class,
                () -> l.format(handler, "Alice", "Bob"));
        assertTrue(ex.getMessage().contains("numeric"));
    }

    // ── Escape sequences ─────────────────────────────────────────────────────

    @Test
    void doublePercent_isNotMistakenForPlaceholder() {
        // "%%d" is a literal "%d", not a placeholder. The real numeric arg is at pos 2.
        String handler = "Discount %s = %d%%";
        Lalein l = build(handler, form(
                "o", "Discount %1$s = %2$d%% (one)",
                "r", "Discount %1$s = %2$d%%"));
        assertEquals("Discount A = 1% (one)", l.format(handler, "A", 1));
        assertEquals("Discount A = 50%",      l.format(handler, "A", 50));
    }

    @Test
    void newlinePlaceholder_isNotADriver() {
        // %n produces a newline and does NOT consume an argument.
        String handler = "Items: %d%nTotal: %s";
        Lalein l = build(handler, form(
                "o", "Items: %1$d%nTotal: %2$s (one)",
                "r", "Items: %1$d%nTotal: %2$s"));
        // Driver should be arg 1 (the %d)
        String out = l.format(handler, 5, "$50");
        assertTrue(out.contains("Items: 5"));
        assertTrue(out.contains("Total: $50"));
    }

    // ── Modifiers (flags / width / precision) ────────────────────────────────

    @Test
    void widthAndPrecisionModifiers_areHandled() {
        String handler = "Score: %-10s = %5.2f";
        Lalein l = build(handler, form(
                "o", "%1$-10s = %2$5.2f (one)",
                "r", "%1$-10s = %2$5.2f"));
        assertEquals("Alice      =  1.00 (one)", l.format(handler, "Alice", 1.0));
        assertEquals("Alice      =  3.14",        l.format(handler, "Alice", 3.14));
    }

    @Test
    void zeroPaddedWidth_isHandled() {
        String handler = "ID %05d records";
        Lalein l = build(handler, form(
                "o", "ID %05d (one record)",
                "r", "ID %05d records"));
        assertEquals("ID 00001 (one record)", l.format(handler, 1));
        assertEquals("ID 00042 records",     l.format(handler, 42));
    }

    // ── Positional refs in the handler ───────────────────────────────────────

    @Test
    void handler_positionalRef_isRespected() {
        // Handler uses %2$d explicitly — first numeric is at index 2.
        String handler = "%1$s has %2$d apples";
        Lalein l = build(handler, form(
                "o", "%1$s has one apple",
                "r", "%1$s has %2$d apples"));
        assertEquals("Alice has one apple", l.format(handler, "Alice", 1));
        assertEquals("Alice has 7 apples",  l.format(handler, "Alice", 7));
    }

    @Test
    void handler_reordersIndicesViaPositional() {
        // Positional refs put numeric at slot 1 even though it appears later textually.
        String handler = "%2$s saved %1$d files";
        Lalein l = build(handler, form(
                "o", "%2$s saved 1 file",
                "r", "%2$s saved %1$d files"));
        assertEquals("Alice saved 1 file",   l.format(handler, 1, "Alice"));
        assertEquals("Alice saved 5 files",  l.format(handler, 5, "Alice"));
    }

    // ── Multi-form agreement (every form shares the same %n$d) ───────────────

    @Test
    void allSixForms_sharingSamePositionalRef_unique() {
        String handler = "complex %d %d";
        Lalein l = build(handler, form(
                "z", "zero: %2$d",
                "o", "one: %2$d",
                "t", "two: %2$d",
                "f", "few: %2$d",
                "m", "many: %2$d",
                "r", "rest: %2$d"));
        assertEquals("zero: 0", l.format(handler, 99, 0));
        assertEquals("one: 1",  l.format(handler, 99, 1));
        assertEquals("two: 2",  l.format(handler, 99, 2));
        assertEquals("rest: 9", l.format(handler, 99, 9));
    }

    @Test
    void formsDisagree_acrossForms_isAmbiguous() {
        // z uses %2$d, r uses %3$d → conflict → falls back to handler scan.
        String handler = "%s and %d and %d";
        Lalein l = build(handler, form(
                "o", "one %2$d ignoring %3$d",
                "r", "many %3$d ignoring %2$d"));
        // Forms ambiguous → handler scan picks first numeric (pos 2).
        assertEquals("one 1 ignoring 99", l.format(handler, "x", 1,  99));
        assertEquals("many 99 ignoring 5",  l.format(handler, "x", 5, 99));
    }

    // ── Non-numeric positional refs in forms must NOT count ──────────────────

    @Test
    void formsWithOnlyStringPositionalRef_fallBackToHandler() {
        // Forms reference only %1$s (string) — must NOT be picked as driver.
        // Handler scan finds %d at pos 2.
        String handler = "%s saw %d cats";
        Lalein l = build(handler, form(
                "o", "%1$s saw a cat",
                "r", "%1$s saw cats"));
        assertEquals("Alice saw a cat", l.format(handler, "Alice", 1));
        assertEquals("Alice saw cats",  l.format(handler, "Alice", 7));
    }

    @Test
    void formsMixStringAndNumericPositional_uniqueOnNumeric() {
        // %1$s should be ignored; only %2$d counts → unique numeric = 2.
        String handler = "%s spent %d";
        Lalein l = build(handler, form(
                "o", "%1$s spent one (%2$d)",
                "r", "%1$s spent many (%2$d)"));
        assertEquals("Alice spent one (1)", l.format(handler, "Alice", 1));
        assertEquals("Alice spent many (9)", l.format(handler, "Alice", 9));
    }

    // ── Round-trip preservation ──────────────────────────────────────────────

    @Test
    void roundTrip_autoDerivedIndex_preserved() {
        // Build a Lalein where the auto-derived index is 2.
        // Dump it back to a Map. Re-load. The behaviour must be identical.
        String handler = "%s has %d";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(handler, form(
                "o", "%1$s has 1",
                "r", "%1$s has %2$d"));
        Lalein first = MapLalein.fromMap(data);
        Map<String, Object> dumped = MapLalein.toMap(first);
        Lalein second = MapLalein.fromMap(dumped);

        // Both must yield the same output for the same input.
        assertEquals(first.format(handler, "A", 1), second.format(handler, "A", 1));
        assertEquals(first.format(handler, "A", 7), second.format(handler, "A", 7));
        assertEquals("A has 1", second.format(handler, "A", 1));
        assertEquals("A has 7", second.format(handler, "A", 7));
    }

    @Test
    void roundTrip_classicSingleArg_preserved() {
        String handler = "apples";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(handler, form("o", "one apple", "r", "many apples"));
        Lalein first = MapLalein.fromMap(data);
        Map<String, Object> dumped = MapLalein.toMap(first);
        Lalein second = MapLalein.fromMap(dumped);
        assertEquals("one apple",   second.format(handler, 1));
        assertEquals("many apples", second.format(handler, 5));
    }

    // ── Missing/sparse forms with auto-derive ───────────────────────────────

    @Test
    void onlyOtherForm_usesAutoDerivedIndex() {
        // Even when only `r` is defined, auto-derive must still pick the right index.
        String handler = "%s tasks: %d";
        Lalein l = build(handler, form("r", "%1$s tasks: %2$d"));
        assertEquals("Alice tasks: 5", l.format(handler, "Alice", 5));
    }

    @Test
    void onlyOtherForm_withFormPositional_drivesPlural() {
        // r uses %2$d — that pinpoints the driver even without other forms.
        String handler = "text %d %d";
        Lalein l = build(handler, form("r", "%2$d wins"));
        // Driver = arg 2 (because %2$d is the only positional numeric ref in forms)
        assertEquals("5 wins", l.format(handler, 99, 5));
    }

    // ── Boundary: float values choose correct form ──────────────────────────

    @Test
    void floatDriver_naturalZeroBand() {
        String handler = "%s has %.2f apples";
        Lalein l = build(handler, form(
                "z", "%1$s has none",
                "o", "%1$s has one",
                "r", "%1$s has %2$.2f"));
        assertEquals("Alice has none", l.format(handler, "Alice", 0.0));
        assertEquals("Alice has one",  l.format(handler, "Alice", 1.0));
        assertEquals("Alice has 2.50", l.format(handler, "Alice", 2.5));
    }
}
