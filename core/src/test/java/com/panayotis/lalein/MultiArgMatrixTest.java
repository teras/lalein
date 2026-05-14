package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Full matrix of value combinations for templates with 2 and 3 numeric
 * parameters. Each parameter slot ranges over {0, 1, 2, N=5}, producing
 * 4×4 = 16 combos for two arguments and 4×4×4 = 64 for three.
 *
 * Each parameter has distinct z/o/t/r forms so the test can pin down
 * exactly which form was selected per slot.
 */
class MultiArgMatrixTest {

    /** Values to exercise per slot: 0 (ZERO band), 1 (ONE band), 2 (TWO band), 5 (OTHER). */
    private static final int[] VALUES = {0, 1, 2, 5};
    /** Label of the expected form per index in VALUES. */
    private static final String[] FORMS = {"z", "o", "t", "r"};

    /** A resolver that never invents a category — only natural detection applies. */
    private static final PluralResolver NEVER = n -> null;

    // === 2-argument matrix: all 16 combinations ===

    @Test
    void twoArgs_fullMatrix() {
        Lalein l = builder().add("msg", "%{a}+%{b}", params(
                "a", param(1, "Za", "Oa", "Ta", null, null, "Ra"),
                "b", param(2, "Zb", "Ob", "Tb", null, null, "Rb")
        )).build();
        l.setPluralResolver(NEVER);

        for (int i = 0; i < VALUES.length; i++) {
            for (int j = 0; j < VALUES.length; j++) {
                String expected = FORMS[i].toUpperCase() + "a+" + FORMS[j].toUpperCase() + "b";
                assertEquals(expected,
                        l.format("msg", VALUES[i], VALUES[j]),
                        "Failed for (" + VALUES[i] + ", " + VALUES[j] + ")");
            }
        }
    }

    @Test
    void twoArgs_swapped_secondParamFirst() {
        // Index order matters — first param uses second argument
        Lalein l = builder().add("msg", "%{first}+%{second}", params(
                "first",  param(2, "Z1", "O1", "T1", null, null, "R1"),
                "second", param(1, "Z2", "O2", "T2", null, null, "R2")
        )).build();
        l.setPluralResolver(NEVER);

        // arg1=2, arg2=1 → first uses arg2=1 → O1; second uses arg1=2 → T2
        assertEquals("O1+T2", l.format("msg", 2, 1));
        // arg1=0, arg2=5 → first uses arg2=5 → R1; second uses arg1=0 → Z2
        assertEquals("R1+Z2", l.format("msg", 0, 5));
    }

    @Test
    void twoArgs_indexReuse_sameArgumentForBothParams() {
        // Both parameters point to argument index 1 (= second-arg-style "^" stripping in DataConverter)
        Lalein l = builder().add("msg", "%{a}+%{b}", params(
                "a", param(1, "Za", "Oa", "Ta", null, null, "Ra"),
                "b", param(1, "Zb", "Ob", "Tb", null, null, "Rb")
        )).build();
        l.setPluralResolver(NEVER);

        for (int i = 0; i < VALUES.length; i++) {
            String upper = FORMS[i].toUpperCase();
            assertEquals(upper + "a+" + upper + "b",
                    l.format("msg", VALUES[i]),
                    "Failed for value " + VALUES[i]);
        }
    }

    @Test
    void twoArgs_someFormsMissing_fallsToOther() {
        // a defines z+o+r, but no t. b defines o+r only.
        Lalein l = builder().add("msg", "%{a}+%{b}", params(
                "a", param(1, "Za", "Oa", null, null, null, "Ra"),
                "b", param(2, null, "Ob", null, null, null, "Rb")
        )).build();
        l.setPluralResolver(NEVER);

        // value 2 in slot a falls through to Ra; value 0 in slot b natural ZERO but z null → Rb
        assertEquals("Ra+Rb", l.format("msg", 2, 0));
        assertEquals("Za+Ob", l.format("msg", 0, 1));
        assertEquals("Oa+Rb", l.format("msg", 1, 2));
        assertEquals("Ra+Rb", l.format("msg", 5, 5));
    }

    @Test
    void twoArgs_nestedReference_outerSelectsInner() {
        // Outer's text references inner; only the outer's branch determines which inner shows.
        Lalein l = builder().add("msg", "%{outer}", params(
                "outer", param(1,
                        "no %{inner}",
                        "one %{inner}",
                        "two %{inner}",
                        null, null,
                        "many %{inner}"),
                "inner", param(2, "0i", "1i", "2i", null, null, "Ni")
        )).build();
        l.setPluralResolver(NEVER);

        for (int i = 0; i < VALUES.length; i++) {
            String outerWord = new String[]{"no", "one", "two", "many"}[i];
            for (int j = 0; j < VALUES.length; j++) {
                String innerForm = new String[]{"0i", "1i", "2i", "Ni"}[j];
                String expected = outerWord + " " + innerForm;
                assertEquals(expected,
                        l.format("msg", VALUES[i], VALUES[j]),
                        "Failed (" + VALUES[i] + ", " + VALUES[j] + ")");
            }
        }
    }

    // === 3-argument matrix: all 64 combinations ===

    @Test
    void threeArgs_fullMatrix() {
        Lalein l = builder().add("msg", "%{a}+%{b}+%{c}", params(
                "a", param(1, "Za", "Oa", "Ta", null, null, "Ra"),
                "b", param(2, "Zb", "Ob", "Tb", null, null, "Rb"),
                "c", param(3, "Zc", "Oc", "Tc", null, null, "Rc")
        )).build();
        l.setPluralResolver(NEVER);

        for (int i = 0; i < VALUES.length; i++) {
            for (int j = 0; j < VALUES.length; j++) {
                for (int k = 0; k < VALUES.length; k++) {
                    String expected =
                            FORMS[i].toUpperCase() + "a+" +
                            FORMS[j].toUpperCase() + "b+" +
                            FORMS[k].toUpperCase() + "c";
                    assertEquals(expected,
                            l.format("msg", VALUES[i], VALUES[j], VALUES[k]),
                            "Failed (" + VALUES[i] + ", " + VALUES[j] + ", " + VALUES[k] + ")");
                }
            }
        }
    }

    @Test
    void threeArgs_indexReuse_allPointToSameSlot() {
        // Three parameters all reading argument index 2 (the middle arg).
        Lalein l = builder().add("msg", "%{a}+%{b}+%{c}", params(
                "a", param(2, "Za", "Oa", "Ta", null, null, "Ra"),
                "b", param(2, "Zb", "Ob", "Tb", null, null, "Rb"),
                "c", param(2, "Zc", "Oc", "Tc", null, null, "Rc")
        )).build();
        l.setPluralResolver(NEVER);

        for (int j = 0; j < VALUES.length; j++) {
            String f = FORMS[j].toUpperCase();
            // First arg is ignored (no param at index 1), third arg is ignored too
            assertEquals(f + "a+" + f + "b+" + f + "c",
                    l.format("msg", 99, VALUES[j], 77),
                    "Failed for middle value " + VALUES[j]);
        }
    }

    @Test
    void threeArgs_chainedNesting_outerToMiddleToInner() {
        // %{a} expands to text containing %{b}, which expands to text containing %{c}.
        Lalein l = builder().add("msg", "%{a}", params(
                "a", param(1, null, "[%{b}]", null, null, null, "(%{b})"),
                "b", param(2, null, "<%{c}>", null, null, null, "{%{c}}"),
                "c", param(3, null, "core", null, null, null, "tail")
        )).build();
        l.setPluralResolver(NEVER);

        assertEquals("[<core>]",  l.format("msg", 1, 1, 1));
        assertEquals("(<core>)",  l.format("msg", 5, 1, 1));
        assertEquals("[{tail}]",  l.format("msg", 1, 5, 5));
        assertEquals("({tail})",  l.format("msg", 5, 5, 5));
    }

    @Test
    void threeArgs_withCustomLanguageResolver_french() {
        // Sanity check that 3-arg flows still respect the configured resolver.
        // French rule: n < 2 → ONE. For values 0, 1, 1.5 → ONE; 2, 5 → OTHER (natural ZERO/TWO if defined).
        Lalein l = builder().add("msg", "%{a}+%{b}+%{c}", params(
                "a", param(1, null, "Oa", null, null, null, "Ra"),
                "b", param(2, null, "Ob", null, null, null, "Rb"),
                "c", param(3, null, "Oc", null, null, null, "Rc")
        )).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));

        // (0, 1, 1.5): all match fr rule → ONE → o-forms
        assertEquals("Oa+Ob+Oc", l.format("msg", 0, 1, 1.5));
        // (2, 5, 1): 2 and 5 not in fr rule → r; 1 → o
        assertEquals("Ra+Rb+Oc", l.format("msg", 2, 5, 1));
    }
}
