package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the optional explicit-index convention: when a parameter map contains
 * a "i" key, its integer value is used as the argument index, overriding the
 * positional/caret inference. Backward compat: omitting "i" preserves the
 * original ordering semantics.
 */
class ExplicitIndexTest {

    @Test
    void simpleParam_explicitIndex_overrides1() {
        Map<String, Object> apples = new LinkedHashMap<>();
        apples.put("i", 2);                 // use the second argument
        apples.put("z", "no apples");
        apples.put("o", "one apple");
        apples.put("r", "%2$d apples");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apples", apples);
        Lalein l = MapLalein.fromMap(data);
        // first argument ignored; second argument drives the plural
        assertEquals("no apples", l.format("apples", 99, 0));
        assertEquals("one apple", l.format("apples", 99, 1));
        assertEquals("5 apples", l.format("apples", 99, 5));
    }

    @Test
    void multiParam_explicitIndex_orderIndependent() {
        // Declare oranges before baskets in the map, but force baskets=1, oranges=2.
        Map<String, Object> oranges = new LinkedHashMap<>();
        oranges.put("i", 2);
        oranges.put("z", "without oranges");
        oranges.put("o", "with one orange");
        oranges.put("r", "with %2$d oranges");

        Map<String, Object> baskets = new LinkedHashMap<>();
        baskets.put("i", 1);
        baskets.put("z", "no basket");
        baskets.put("o", "a basket %{oranges}");
        baskets.put("r", "%1$d baskets %{oranges}");

        Map<String, Object> bwo = new LinkedHashMap<>();
        bwo.put("oranges", oranges);    // oranges first in iteration order — yet i=2
        bwo.put("baskets", baskets);     // baskets second — yet i=1
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bwo", bwo);

        Lalein l = MapLalein.fromMap(data);

        // First key seen is "oranges" → format = "%{oranges}". But i puts it on arg2.
        // For format(bwo, baskets#, oranges#): we expect oranges form selected by arg2.
        assertEquals("with one orange", l.format("bwo", 99, 1));
        assertEquals("without oranges", l.format("bwo", 99, 0));
        assertEquals("with 7 oranges", l.format("bwo", 99, 7));
    }

    @Test
    void multiParam_explicitIndex_shareIndexExplicitly() {
        // Two params, both with i=2 → both read the second argument.
        Map<String, Object> oranges = new LinkedHashMap<>();
        oranges.put("i", 2);
        oranges.put("z", "no oranges");
        oranges.put("o", "one orange");
        oranges.put("r", "%2$d oranges");

        Map<String, Object> orangesAlt = new LinkedHashMap<>();
        orangesAlt.put("i", 2);              // explicit share, no caret needed
        orangesAlt.put("z", "ALT_zero");
        orangesAlt.put("o", "ALT_one");
        orangesAlt.put("r", "ALT_other");

        Map<String, Object> baskets = new LinkedHashMap<>();
        baskets.put("i", 1);
        baskets.put("z", "no_basket %{orangesAlt}");
        baskets.put("o", "a_basket %{oranges}");
        baskets.put("r", "%1$d_baskets %{oranges}");

        Map<String, Object> bwo = new LinkedHashMap<>();
        bwo.put("baskets", baskets);
        bwo.put("oranges", oranges);
        bwo.put("orangesAlt", orangesAlt);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bwo", bwo);

        Lalein l = MapLalein.fromMap(data);

        // arg1=0 → baskets's z branch → "no_basket ALT_one" (orangesAlt at arg2=1 → o)
        assertEquals("no_basket ALT_one", l.format("bwo", 0, 1));
        // arg1=1 → baskets's o branch → "a_basket one orange"
        assertEquals("a_basket one orange", l.format("bwo", 1, 1));
        // arg1=3 → baskets's r branch → "3_baskets 5 oranges"
        assertEquals("3_baskets 5 oranges", l.format("bwo", 3, 5));
    }

    @Test
    void multiParam_mixed_someExplicitSomeImplicit() {
        // First param implicit (gets index 1), second explicit i=3 (skip 2), third implicit (gets 4)
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("z", "a0"); a.put("o", "a1"); a.put("r", "aN");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("i", 3);
        b.put("z", "b0"); b.put("o", "b1"); b.put("r", "bN");

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("z", "c0"); c.put("o", "c1"); c.put("r", "cN");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("a", a);
        msg.put("b", b);
        msg.put("c", c);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("msg", msg);

        Lalein l = MapLalein.fromMap(data);

        // (arg1=1, arg2=ignored, arg3=0, arg4=1)
        //  → a picks arg1=1 → "a1"
        // First key is "a" → format = "%{a}". The other params (b, c) live in registry too,
        // but the format only includes %{a}, so only a is materialised.
        assertEquals("a1", l.format("msg", 1, 99, 0, 1));
    }

    @Test
    void explicitIndex_caretIgnoredWhenIPresent() {
        // ^foo with i=5 should follow i (=5), not the previous-index from ^
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", "f0"); first.put("o", "f1"); first.put("r", "fN");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("i", 5);
        second.put("z", "s0"); second.put("o", "s1"); second.put("r", "sN");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("first", first);
        msg.put("^second", second);   // would normally inherit index 1, but i overrides to 5

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("msg", msg);
        Lalein l = MapLalein.fromMap(data);

        // Format uses first key → "%{first}". The second param needs args[4] (= 5th).
        // first reads arg1 = 2 → "fN"
        assertEquals("fN", l.format("msg", 2, 0, 0, 0, 99));
    }

    @Test
    void backwardCompat_noUnderscoreI_originalBehavior() {
        // Without i, the existing positional/caret rules still apply.
        Map<String, Object> baskets = new LinkedHashMap<>();
        baskets.put("z", "b0 %{oranges}"); baskets.put("o", "b1 %{oranges}"); baskets.put("r", "bN %{oranges}");

        Map<String, Object> oranges = new LinkedHashMap<>();
        oranges.put("z", "o0"); oranges.put("o", "o1"); oranges.put("r", "oN");

        Map<String, Object> bwo = new LinkedHashMap<>();
        bwo.put("baskets", baskets);      // implicit index 1
        bwo.put("oranges", oranges);      // implicit index 2

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bwo", bwo);
        Lalein l = MapLalein.fromMap(data);

        assertEquals("b0 o0", l.format("bwo", 0, 0));
        assertEquals("b1 o1", l.format("bwo", 1, 1));
        assertEquals("bN oN", l.format("bwo", 5, 5));
    }

    @Test
    void underscoreIisNotValidatedAsPluralTag() {
        // Sanity: presence of i must not trigger "Unknown tag" exception.
        Map<String, Object> apples = new LinkedHashMap<>();
        apples.put("i", 1);
        apples.put("o", "one");
        apples.put("r", "many");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apples", apples);
        // Should build without throwing
        Lalein l = MapLalein.fromMap(data);
        assertEquals("one", l.format("apples", 1));
        assertEquals("many", l.format("apples", 5));
    }
}
