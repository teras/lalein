package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the optional explicit master-template convention: when a multi-parameter
 * translation map contains a "format" key, its String value is used as the root
 * format template, overriding the implicit "%{firstKey}" inference.
 *
 * Exercises every scenario that motivated adding this feature:
 *   - multiple top-level params in the template
 *   - static text outside param expansions
 *   - param references in any order (incl. reversed vs. argument order)
 *   - same param referenced multiple times
 *   - combined with explicit "i" indices
 *   - mixing %{name} with positional %d / %1$d
 *   - 3+ params and chained nesting
 *   - special characters in the template
 *   - error handling when "format" is misplaced
 *   - round-trip preservation in both directions
 *   - backward compat: default behaviour when "format" is absent
 */
@SuppressWarnings("unchecked")
class ExplicitFormatTest {

    // === Basic motivating cases ===

    @Test
    void explicitFormat_allowsArbitraryWrappingText() {
        Map<String, Object> count = new LinkedHashMap<>();
        count.put("i", 1);
        count.put("z", "no files");
        count.put("o", "one file");
        count.put("r", "%1$d files");

        Map<String, Object> dest = new LinkedHashMap<>();
        dest.put("i", 2);
        dest.put("o", "one folder");
        dest.put("r", "%2$d folders");

        Map<String, Object> filesCopied = new LinkedHashMap<>();
        filesCopied.put("format", "Copying %{count} to %{dest}!");
        filesCopied.put("count", count);
        filesCopied.put("dest", dest);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("files_copied", filesCopied);
        Lalein l = MapLalein.fromMap(data);

        assertEquals("Copying no files to one folder!", l.format("files_copied", 0, 1));
        assertEquals("Copying one file to 3 folders!", l.format("files_copied", 1, 3));
        assertEquals("Copying 7 files to 2 folders!", l.format("files_copied", 7, 2));
    }

    @Test
    void explicitFormat_canPlaceParamsInReversedArgOrder() {
        // Format puts %{noun} (arg2) BEFORE %{count} (arg1)
        Map<String, Object> noun = new LinkedHashMap<>();
        noun.put("i", 2);
        noun.put("o", "apple");
        noun.put("r", "apples");

        Map<String, Object> count = new LinkedHashMap<>();
        count.put("i", 1);
        count.put("z", "No");
        count.put("o", "One");
        count.put("r", "%1$d");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "%{noun}: %{count}");
        msg.put("noun", noun);
        msg.put("count", count);

        Lalein l = wrap("msg", msg);

        assertEquals("apple: One", l.format("msg", 1, 1));
        assertEquals("apples: 5", l.format("msg", 5, 5));
    }

    @Test
    void explicitFormat_withSurroundingStaticText() {
        Map<String, Object> count = new LinkedHashMap<>();
        count.put("i", 1);
        count.put("z", "no");
        count.put("o", "one");
        count.put("r", "%1$d");

        Map<String, Object> kind = new LinkedHashMap<>();
        kind.put("i", 1);
        kind.put("z", "items");
        kind.put("o", "item");
        kind.put("r", "items");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "You have %{count} %{kind} in the cart.");
        msg.put("count", count);
        msg.put("kind", kind);

        Lalein l = wrap("msg", msg);

        assertEquals("You have no items in the cart.", l.format("msg", 0));
        assertEquals("You have one item in the cart.", l.format("msg", 1));
        assertEquals("You have 5 items in the cart.", l.format("msg", 5));
    }

    // === Same param referenced multiple times ===

    @Test
    void explicitFormat_sameParamReferencedTwice() {
        Map<String, Object> word = new LinkedHashMap<>();
        word.put("i", 1);
        word.put("z", "nothing");
        word.put("o", "apple");
        word.put("r", "apples");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "I see %{word} — yes, %{word}!");
        msg.put("word", word);

        Lalein l = wrap("msg", msg);

        assertEquals("I see nothing — yes, nothing!", l.format("msg", 0));
        assertEquals("I see apple — yes, apple!", l.format("msg", 1));
        assertEquals("I see apples — yes, apples!", l.format("msg", 7));
    }

    @Test
    void explicitFormat_sameParamReferencedThreeTimes() {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("i", 1);
        n.put("o", "X");
        n.put("r", "Y");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "%{n}/%{n}/%{n}");
        msg.put("n", n);

        Lalein l = wrap("msg", msg);
        assertEquals("X/X/X", l.format("msg", 1));
        assertEquals("Y/Y/Y", l.format("msg", 5));
    }

    // === Mixing %{} with positional %d / %1$d ===

    @Test
    void explicitFormat_mixedWithPositionalSpecifiers() {
        Map<String, Object> qty = new LinkedHashMap<>();
        qty.put("i", 1);
        qty.put("o", "single");
        qty.put("r", "many");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "[%{qty}] count=%1$d, multiplied=%2$d");
        msg.put("qty", qty);

        Lalein l = wrap("msg", msg);

        // %{qty} resolves first via plural lookup; then %1$d / %2$d fill from args.
        assertEquals("[single] count=1, multiplied=2", l.format("msg", 1, 2));
        assertEquals("[many] count=7, multiplied=14", l.format("msg", 7, 14));
    }

    @Test
    void explicitFormat_mixedWithBareSequentialSpecifier() {
        Map<String, Object> qty = new LinkedHashMap<>();
        qty.put("i", 1);
        qty.put("o", "one");
        qty.put("r", "%1$d");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "%{qty} times %d-fold");   // %d is the next sequential, becomes arg1
        msg.put("qty", qty);

        Lalein l = wrap("msg", msg);
        // After %{qty} → "one" or "%1$d", String.format(format, 1, 2):
        //   "one times %d-fold" with args(1,2) → "one times 1-fold"
        //   "%1$d times %d-fold" with args(7,2) → "7 times 7-fold"
        assertEquals("one times 1-fold", l.format("msg", 1, 2));
        assertEquals("7 times 7-fold", l.format("msg", 7, 2));
    }

    // === Combined with explicit "i" ===

    @Test
    void explicitFormat_combinedWith_i_keys_orderIndependent() {
        // Declare params in the "wrong" order, with explicit i values
        Map<String, Object> dest = new LinkedHashMap<>();
        dest.put("i", 2);
        dest.put("o", "folder");
        dest.put("r", "folders");

        Map<String, Object> src = new LinkedHashMap<>();
        src.put("i", 1);
        src.put("o", "file");
        src.put("r", "files");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "From %{src} to %{dest}: %1$d → %2$d");
        msg.put("dest", dest);   // dest declared first
        msg.put("src", src);     // src declared second

        Lalein l = wrap("msg", msg);

        assertEquals("From file to folder: 1 → 1", l.format("msg", 1, 1));
        assertEquals("From files to folders: 7 → 3", l.format("msg", 7, 3));
    }

    // === Three+ params ===

    @Test
    void explicitFormat_threeParams_independent() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("i", 1); a.put("o", "Aone"); a.put("r", "Amany");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("i", 2); b.put("o", "Bone"); b.put("r", "Bmany");

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("i", 3); c.put("o", "Cone"); c.put("r", "Cmany");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "<%{a}|%{b}|%{c}>");
        msg.put("a", a); msg.put("b", b); msg.put("c", c);

        Lalein l = wrap("msg", msg);
        assertEquals("<Aone|Bone|Cone>", l.format("msg", 1, 1, 1));
        assertEquals("<Amany|Bmany|Cmany>", l.format("msg", 5, 5, 5));
        assertEquals("<Aone|Bmany|Cone>", l.format("msg", 1, 5, 1));
    }

    @Test
    void explicitFormat_chainedNestedExpansion() {
        // format expands to text containing %{outer}; outer's plural form contains %{inner}.
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("i", 2);
        inner.put("o", "leaf");
        inner.put("r", "leaves");

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("i", 1);
        outer.put("o", "[%{inner}]");
        outer.put("r", "{%{inner}}");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "Top: %{outer} -- done.");
        msg.put("outer", outer);
        msg.put("inner", inner);

        Lalein l = wrap("msg", msg);
        assertEquals("Top: [leaf] -- done.", l.format("msg", 1, 1));
        assertEquals("Top: {leaves} -- done.", l.format("msg", 5, 5));
    }

    // === Special characters in the template ===

    @Test
    void explicitFormat_withSpecialChars() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("i", 1);
        p.put("o", "X");
        p.put("r", "Y");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "line1\n\tline2: %{p}\n\t— ok");
        msg.put("p", p);

        Lalein l = wrap("msg", msg);
        assertEquals("line1\n\tline2: X\n\t— ok", l.format("msg", 1));
    }

    @Test
    void explicitFormat_withUnicode() {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("i", 1);
        n.put("o", "ένα");
        n.put("r", "πολλά");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "Έχω %{n} μήλα στο τραπέζι. 🍎");
        msg.put("n", n);

        Lalein l = wrap("msg", msg);
        assertEquals("Έχω ένα μήλα στο τραπέζι. 🍎", l.format("msg", 1));
        assertEquals("Έχω πολλά μήλα στο τραπέζι. 🍎", l.format("msg", 5));
    }

    // === Single-param under multi-param structure ===

    @Test
    void explicitFormat_singleParam_inMultiParamShape() {
        // Using format together with a single sub-map param — atypical but valid.
        Map<String, Object> only = new LinkedHashMap<>();
        only.put("i", 1);
        only.put("o", "uno");
        only.put("r", "muchos");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "Hello, you have %{only} reason.");
        msg.put("only", only);

        Lalein l = wrap("msg", msg);
        assertEquals("Hello, you have uno reason.", l.format("msg", 1));
        assertEquals("Hello, you have muchos reason.", l.format("msg", 5));
    }

    // === Backward compat: format absent ===

    @Test
    void noFormat_defaultsToFirstKeyWrapping() {
        Map<String, Object> baskets = new LinkedHashMap<>();
        baskets.put("z", "no_basket %{oranges}");
        baskets.put("o", "a_basket %{oranges}");
        baskets.put("r", "%1$d_baskets %{oranges}");

        Map<String, Object> oranges = new LinkedHashMap<>();
        oranges.put("z", "no_oranges");
        oranges.put("o", "one_orange");
        oranges.put("r", "%2$d_oranges");

        Map<String, Object> bwo = new LinkedHashMap<>();
        bwo.put("baskets", baskets);
        bwo.put("oranges", oranges);

        Lalein l = wrap("bwo", bwo);

        assertEquals("no_basket no_oranges", l.format("bwo", 0, 0));
        assertEquals("a_basket one_orange", l.format("bwo", 1, 1));
        assertEquals("3_baskets 5_oranges", l.format("bwo", 3, 5));
    }

    @Test
    void noFormat_andCaretPrefix_originalBehavior() {
        // Verify the classic ^-prefix still works alongside our new opt-in keys.
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("o", "[%{b}]"); a.put("r", "{%{b}}");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("o", "X"); b.put("r", "Y");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("a", a);
        msg.put("^b", b);          // share argument with 'a' (= index 1)

        Lalein l = wrap("msg", msg);
        assertEquals("[X]", l.format("msg", 1));
        assertEquals("{Y}", l.format("msg", 5));
    }

    // === Round-trip in both directions ===

    @Test
    void roundTrip_explicitFormat_isPreserved() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("i", 1);
        a.put("o", "single");
        a.put("r", "many");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("i", 2);
        b.put("o", "one");
        b.put("r", "%2$d");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "Got %{a} for %{b} reasons");
        msg.put("a", a);
        msg.put("b", b);

        Lalein original = wrap("msg", msg);

        Map<String, Object> written = MapLalein.toMap(original);
        Map<String, Object> writtenMsg = (Map<String, Object>) written.get("msg");
        assertEquals("Got %{a} for %{b} reasons", writtenMsg.get("format"));

        Lalein reread = MapLalein.fromMap(written);
        assertEquals(original, reread);
        // second cycle stability
        assertEquals(original, MapLalein.fromMap(MapLalein.toMap(reread)));
    }

    @Test
    void roundTrip_defaultFormat_omitsFormatKey() {
        // When the format is exactly "%{firstKey}", the writer must NOT emit "format".
        Map<String, Object> baskets = new LinkedHashMap<>();
        baskets.put("o", "a_basket %{oranges}");
        baskets.put("r", "%1$d_baskets %{oranges}");

        Map<String, Object> oranges = new LinkedHashMap<>();
        oranges.put("o", "one");
        oranges.put("r", "%2$d");

        Map<String, Object> bwo = new LinkedHashMap<>();
        bwo.put("baskets", baskets);
        bwo.put("oranges", oranges);

        Lalein l = wrap("bwo", bwo);

        Map<String, Object> written = MapLalein.toMap(l);
        Map<String, Object> writtenBwo = (Map<String, Object>) written.get("bwo");
        assertFalse(writtenBwo.containsKey("format"),
                "writer should omit 'format' when value is the default %{firstKey}");
    }

    @Test
    void roundTrip_formatThatHappensToEqualFirstKey_loseEqualSemantics() {
        // Sanity: if the user supplies format that is literally "%{firstKey}",
        // the writer treats it as default and omits the key. The reader then
        // rebuilds it as default. Result is identical → still semantically equal.
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("o", "x"); a.put("r", "y");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("o", "u"); b.put("r", "v");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "%{a}");          // happens to equal default
        msg.put("a", a);
        msg.put("b", b);

        Lalein original = wrap("msg", msg);
        Map<String, Object> written = MapLalein.toMap(original);
        Map<String, Object> writtenMsg = (Map<String, Object>) written.get("msg");
        assertFalse(writtenMsg.containsKey("format"));
        assertEquals(original, MapLalein.fromMap(written));
    }

    // === Misuse / error paths ===

    @Test
    void formatInSimpleShape_throws() {
        // "format" is a reserved keyword and cannot appear inside a short-form
        // parameter (it only makes sense at the top level of a complex translation
        // as the master template). The loader rejects it with a clear message.
        Map<String, Object> apples = new LinkedHashMap<>();
        apples.put("format", "Hello %{base}");
        apples.put("z", "no apples");
        apples.put("o", "one apple");
        apples.put("r", "%d apples");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apples", apples);
        LaleinException ex = assertThrows(LaleinException.class, () -> MapLalein.fromMap(data));
        assertTrue(ex.getMessage().contains("format"));
    }

    @Test
    void formatWithUnknownReference_throwsAtFormatTime() {
        // format references %{ghost} — no such param defined. Should throw when format() runs.
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("o", "x"); a.put("r", "y");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "Hello %{ghost}");
        msg.put("a", a);

        Lalein l = wrap("msg", msg);
        assertThrows(LaleinException.class, () -> l.format("msg", 1));
    }

    @Test
    void emptyFormat_yieldsEmptyOutput() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("o", "x"); a.put("r", "y");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "");
        msg.put("a", a);

        Lalein l = wrap("msg", msg);
        // No %{} references in format → just an empty string passed to String.format
        assertEquals("", l.format("msg", 1));
    }

    @Test
    void formatWithoutAnyParamReference_returnsLiteralText() {
        // format references no params at all — pure static text.
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("o", "x"); a.put("r", "y");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "static and final");
        msg.put("a", a);

        Lalein l = wrap("msg", msg);
        assertEquals("static and final", l.format("msg", 1));
    }

    // helpers
    private static Lalein wrap(String handler, Map<String, Object> data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(handler, data);
        return MapLalein.fromMap(root);
    }
}
