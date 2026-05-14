package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-parameter and nested-template expansion: parameters referencing other
 * parameters via %{name}, index reuse, and recursion depth.
 */
class NestedTemplateTest {

    @Test
    void singleNesting() {
        Lalein l = builder().add("msg", "%{outer}", params(
                "outer", param(1,
                        "no %{inner}",
                        "one %{inner}",
                        null, null, null,
                        "%1$d %{inner}"),
                "inner", param(1,
                        "items",
                        "item",
                        null, null, null,
                        "items")
        )).build();
        assertEquals("no items", l.format("msg", 0));
        assertEquals("one item", l.format("msg", 1));
        assertEquals("5 items", l.format("msg", 5));
    }

    @Test
    void twoIndependentIndexes() {
        Lalein l = builder().add("msg", "%{a}", params(
                "a", param(1,
                        "no a, %{b}",
                        "one a, %{b}",
                        null, null, null,
                        "%1$d a, %{b}"),
                "b", param(2,
                        "no b",
                        "one b",
                        null, null, null,
                        "%2$d b")
        )).build();
        assertEquals("no a, no b", l.format("msg", 0, 0));
        assertEquals("one a, one b", l.format("msg", 1, 1));
        assertEquals("3 a, 7 b", l.format("msg", 3, 7));
        assertEquals("no a, 5 b", l.format("msg", 0, 5));
    }

    @Test
    void threeLevelNesting() {
        Lalein l = builder().add("msg", "%{lvl1}", params(
                "lvl1", param(1, null, "one[%{lvl2}]", null, null, null, "many[%{lvl2}]"),
                "lvl2", param(1, null, "alpha(%{lvl3})", null, null, null, "beta(%{lvl3})"),
                "lvl3", param(1, null, "X", null, null, null, "Y")
        )).build();
        assertEquals("one[alpha(X)]", l.format("msg", 1));
        assertEquals("many[beta(Y)]", l.format("msg", 5));
    }

    @Test
    void sameParameterReferencedTwiceInFormat() {
        Lalein l = builder().add("msg", "%{p} and again %{p}", params(
                "p", param(1, "zero", "one", null, null, null, "many")
        )).build();
        // The current resolver expands one occurrence per iteration; both should expand.
        assertEquals("zero and again zero", l.format("msg", 0));
        assertEquals("one and again one", l.format("msg", 1));
        assertEquals("many and again many", l.format("msg", 9));
    }

    @Test
    void unknownNestedParameter_throws() {
        Lalein l = builder().add("msg", "%{outer}", params(
                "outer", param(1, null, "see %{ghost}", null, null, null, "see %{ghost}")
        )).build();
        assertThrows(LaleinException.class, () -> l.format("msg", 1));
    }

    @Test
    void positionalAndSequentialMixed() {
        Lalein l = builder().add("msg", "%{x}", params(
                "x", param(1, null, "first=%d", null, null, null, "first=%1$d second=%2$s")
        )).build();
        assertEquals("first=1", l.format("msg", 1, "ignored"));
        assertEquals("first=5 second=hi", l.format("msg", 5, "hi"));
    }
}
