package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auto-derive of the short-form argumentIndex.
 *
 * Lookup order: (1) unique positional numeric ref %n$d in plural forms,
 * (2) first numeric placeholder in the handler, (3) fallback 1.
 * Explicit "i" key always wins over auto-derive.
 */
class AutoIndexTest {

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

    // ── Step 1: forms-scan ───────────────────────────────────────────────────

    @Test
    void formsScan_uniquePositionalRef_picksThatIndex() {
        // FINDINGS scenario: handler has %s at pos 1, %d at pos 2; forms use %2$d
        String handler = "Cash payment of %s saved (%d month(s) allocated)";
        Lalein l = build(handler, form(
                "o", "Payment of %1$s saved (1 month allocated)",
                "r", "Payment of %1$s saved (%2$d months allocated)"));
        assertEquals("Payment of 1.23 saved (1 month allocated)",
                l.format(handler, "1.23", 1));
        assertEquals("Payment of 1.23 saved (5 months allocated)",
                l.format(handler, "1.23", 5));
    }

    @Test
    void formsScan_picksSecondIndex_inAmbiguousHandler() {
        // handler has two %d; only %2$d appears in forms → drive = 2
        String handler = "text %d %d";
        Lalein l = build(handler, form(
                "o", "single %2$d",
                "r", "many %2$d"));
        assertEquals("single 1", l.format(handler, 99, 1));
        assertEquals("many 7",   l.format(handler, 99, 7));
    }

    @Test
    void formsScan_picksFirstIndex_whenOnlyPos1Referenced() {
        // forms reference only %1$d → drive = 1
        String handler = "text %d %d";
        Lalein l = build(handler, form(
                "o", "%1$d single",
                "r", "%1$d many"));
        assertEquals("1 single", l.format(handler, 1, 99));
        assertEquals("3 many",   l.format(handler, 3, 99));
    }

    @Test
    void formsScan_ignoresStringPositional() {
        // %1$s is non-numeric — must NOT count as a drive ref. %2$d wins.
        String handler = "%s and %d";
        Lalein l = build(handler, form(
                "o", "%1$s + 1",
                "r", "%1$s + %2$d"));
        assertEquals("hi + 1", l.format(handler, "hi", 1));
        assertEquals("hi + 4", l.format(handler, "hi", 4));
    }

    // ── Step 2: handler-scan fallback ────────────────────────────────────────

    @Test
    void handlerScan_firstNumericAtPos2_whenFormsAmbiguous() {
        // Both %1$d and %2$d in forms → ambiguous → fall through to handler scan.
        // Handler "%s + %d" → first numeric is at pos 2.
        String handler = "%s + %d items";
        Lalein l = build(handler, form(
                "o", "%1$s + %2$d item",
                "r", "%1$s + %2$d items"));
        assertEquals("hi + 1 item",  l.format(handler, "hi", 1));
        assertEquals("hi + 9 items", l.format(handler, "hi", 9));
    }

    @Test
    void handlerScan_picksFirstWhenFormsHaveNoPositional() {
        // Forms use plain %d (non-positional) → no positional refs to derive from.
        // Handler scan: first numeric is at pos 1.
        String handler = "I have %d things";
        Lalein l = build(handler, form(
                "o", "I have 1 thing",
                "r", "I have %d things"));
        assertEquals("I have 1 thing",  l.format(handler, 1));
        assertEquals("I have 5 things", l.format(handler, 5));
    }

    // ── Step 3: full fallback ────────────────────────────────────────────────

    @Test
    void fallback_picksOne_whenNoNumericAnywhere() {
        // Classic "apples" key — neither handler nor forms have numeric placeholders.
        String handler = "apples";
        Lalein l = build(handler, form(
                "o", "I have an apple",
                "r", "I have many apples"));
        assertEquals("I have an apple",   l.format(handler, 1));
        assertEquals("I have many apples", l.format(handler, 5));
    }

    // ── Explicit "i" still overrides ─────────────────────────────────────────

    @Test
    void explicitI_overridesAutoDerive() {
        // Forms scan would pick 2 (because of %2$d), but i=1 forces drive on arg1.
        String handler = "text %d %d";
        Lalein l = build(handler, form(
                "i", 1,
                "o", "single %2$d",
                "r", "many %2$d"));
        // Drive = arg1, arg2 used as formatting value only
        assertEquals("single 99", l.format(handler, 1, 99));
        assertEquals("many 99",   l.format(handler, 5, 99));
    }

    @Test
    void explicitI_resolvesAmbiguity() {
        // "Selected %d of %d items" with drive on the 2nd (total): ambiguous → needs i=2
        String handler = "Selected %d of %d items";
        Lalein l = build(handler, form(
                "i", 2,
                "o", "Selected %1$d of %2$d item",
                "r", "Selected %1$d of %2$d items"));
        assertEquals("Selected 0 of 1 item",  l.format(handler, 0, 1));
        assertEquals("Selected 3 of 5 items", l.format(handler, 3, 5));
        assertEquals("Selected 1 of 1 item",  l.format(handler, 1, 1));
    }

    // ── End-to-end FINDINGS repro ────────────────────────────────────────────

    @Test
    void findings_repro_noLongerThrows() {
        // The exact scenario from FINDINGS.md §6: count is the 2nd vararg.
        // In 1.2 this threw LaleinException ("numeric argument required, got String").
        // After auto-derive this works without any "i" key.
        String handler = "Cash payment of %s saved (%d month(s) allocated)";
        Lalein l = build(handler, form(
                "o", "Πληρωμή %1$s αποθηκεύτηκε (1 μήνας ανατέθηκε)",
                "r", "Πληρωμή %1$s αποθηκεύτηκε (%2$d μήνες ανατέθηκαν)"));
        assertEquals("Πληρωμή 1.23 αποθηκεύτηκε (1 μήνας ανατέθηκε)",
                l.format(handler, "1.23", 1));
        assertEquals("Πληρωμή 1.23 αποθηκεύτηκε (12 μήνες ανατέθηκαν)",
                l.format(handler, "1.23", 12));
    }

    // ── Exception context ────────────────────────────────────────────────────

    @Test
    void exception_includesHandlerAndParamName() {
        String handler = "apples";
        Lalein l = build(handler, form(
                "o", "one apple",
                "r", "%d apples"));
        LaleinException ex = assertThrows(LaleinException.class,
                () -> l.format(handler, "not a number"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("apples"), "expected handler in message, got: " + msg);
        assertTrue(msg.contains("base"),   "expected param name in message, got: " + msg);
    }
}
