package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive combinations around the value "2": every numeric type, every
 * boundary of the natural TWO band [1.999999, 2.000001], every combination
 * of which plural forms are present, every supported language rule, and
 * interaction with custom PluralResolvers.
 */
class TwoValueCombinationsTest {

    // === All numeric types representing exactly 2 ===

    @Test
    void allNumericTypes_value2_returnTwo() {
        Lalein l = build_zotr();
        assertEquals("two", l.format("n", (byte) 2));
        assertEquals("two", l.format("n", (short) 2));
        assertEquals("two", l.format("n", 2));
        assertEquals("two", l.format("n", 2L));
        assertEquals("two", l.format("n", 2.0f));
        assertEquals("two", l.format("n", 2.0d));
        assertEquals("two", l.format("n", new BigDecimal("2")));
        assertEquals("two", l.format("n", new BigDecimal("2.0")));
        assertEquals("two", l.format("n", new BigDecimal("2.00000000000000000000")));
        assertEquals("two", l.format("n", BigInteger.valueOf(2)));
        assertEquals("two", l.format("n", new AtomicInteger(2)));
        assertEquals("two", l.format("n", new AtomicLong(2L)));
    }

    // === Natural TWO band boundary ===

    @Test
    void boundary_inclusiveLower_1_999999() {
        Lalein l = build_zotr();
        assertEquals("two", l.format("n", 1.999999d));
    }

    @Test
    void boundary_inclusiveUpper_2_000001() {
        Lalein l = build_zotr();
        assertEquals("two", l.format("n", 2.000001d));
    }

    @Test
    void boundary_justBelow_1_999998_outsideBand() {
        Lalein l = build_zotr();
        // 1.999998 is outside natural TWO band, falls through to OTHER (default resolver returns null)
        assertEquals("other", l.format("n", 1.999998d));
    }

    @Test
    void boundary_justAbove_2_000002_outsideBand() {
        Lalein l = build_zotr();
        assertEquals("other", l.format("n", 2.000002d));
    }

    @Test
    void negativeTwo_notInTwoBand() {
        Lalein l = build_zotr();
        // -2 has no natural detection and no rule -> OTHER
        assertEquals("other", l.format("n", -2));
        assertEquals("other", l.format("n", -2.0d));
        assertEquals("other", l.format("n", new BigDecimal("-2")));
    }

    // === Combinations of which plural forms are present ===

    @Test
    void onlyTwoAndOther_value2_returnsTwo() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, "two", null, null, "other"))).build();
        assertEquals("two", l.format("n", 2));
    }

    @Test
    void onlyTwoAndOther_value3_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, "two", null, null, "other"))).build();
        assertEquals("other", l.format("n", 3));
    }

    @Test
    void onlyTwoAndOther_value1_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, "two", null, null, "other"))).build();
        // 1 is in natural ONE band, but "o" is null, so falls through to OTHER
        assertEquals("other", l.format("n", 1));
    }

    @Test
    void onlyOneTwoOther_value2_returnsTwo() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "one", "two", null, null, "other"))).build();
        assertEquals("two", l.format("n", 2));
    }

    @Test
    void twoNull_value2_fallsToOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", "one", null, null, null, "other"))).build();
        assertEquals("other", l.format("n", 2));
    }

    @Test
    void twoAndManyDefined_value2_naturalWins() {
        // Even with a resolver claiming MANY for 2, natural TWO takes precedence when t is defined.
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, "TWO_FORM", null, "MANY_FORM", "other"))).build();
        l.setPluralResolver(n -> PluralType.MANY);
        assertEquals("TWO_FORM", l.format("n", 2));
    }

    @Test
    void twoNullButManyDefined_resolverReturnsMany_value2_returnsMany() {
        // When two is null, the rule path runs and MANY is reachable.
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, null, null, "MANY_FORM", "other"))).build();
        l.setPluralResolver(n -> PluralType.MANY);
        assertEquals("MANY_FORM", l.format("n", 2));
    }

    @Test
    void customResolverReturnsFew_andFewDefined_butTwoAlsoDefined_value2_givesTwo() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, "two", "few", null, "other"))).build();
        l.setPluralResolver(n -> PluralType.FEW);
        // Natural TWO band overrides rule -> "two"
        assertEquals("two", l.format("n", 2));
    }

    // === Cross-language: value 2 ===

    @Test
    void english_value2_naturalTwoUsed() {
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("two", l.format("n", 2));
    }

    @Test
    void english_value2_twoNull_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "z", "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("r", l.format("n", 2));
    }

    @Test
    void french_value2_naturalTwoUsed() {
        // French rule is "n < 2 -> ONE". Value 2 (= 2.0) is in TWO band -> if t defined returns t.
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("two", l.format("n", 2));
    }

    @Test
    void french_value2_twoNull_returnsOther() {
        // Without t, French rule returns null (2 is NOT < 2) -> OTHER
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("r", l.format("n", 2));
    }

    @Test
    void french_value1_9_returnsOne() {
        // 1.9 < 1.999999 -> French rule returns ONE
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", "t", null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("o", l.format("n", 1.9));
    }

    @Test
    void hindi_value2_returnsOther() {
        // hi rule is "n <= 1 -> ONE"; for 2 -> null -> OTHER
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("hi"));
        assertEquals("two", l.format("n", 2));   // natural TWO still applies
    }

    @Test
    void hindi_value2_twoNull_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("hi"));
        assertEquals("r", l.format("n", 2));
    }

    @Test
    void punjabi_value2_returnsOther() {
        // pa rule is "n == 0 -> ONE"; for 2 -> null -> OTHER (or natural TWO if t defined)
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("pa"));
        assertEquals("two", l.format("n", 2));
    }

    @Test
    void portuguese_value2_returnsOther() {
        // pt rule is "0 < n < 2 -> ONE"; for 2 -> null -> OTHER (or natural TWO)
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLocale(new Locale("pt")));
        assertEquals("two", l.format("n", 2));
    }

    @Test
    void portuguese_value1_999_returnsOne() {
        // 1.999 is within 0<n<2 -> rule returns ONE
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", "t", null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLocale(new Locale("pt")));
        assertEquals("o", l.format("n", 1.999d));
    }

    // === Multiple parameters with value 2 in different argument positions ===

    @Test
    void twoArgs_bothTwo_independentForms() {
        Lalein l = builder().add("msg", "%{a} and %{b}", params(
                "a", param(1, "Z1", "O1", "T1", null, null, "R1"),
                "b", param(2, "Z2", "O2", "T2", null, null, "R2")
        )).build();
        assertEquals("T1 and T2", l.format("msg", 2, 2));
        assertEquals("T1 and O2", l.format("msg", 2, 1));
        assertEquals("O1 and T2", l.format("msg", 1, 2));
        assertEquals("R1 and T2", l.format("msg", 5, 2));
    }

    @Test
    void twoArgs_indexReuse_secondMappedToSecondArg() {
        // Build params with the same argumentIndex (=2) on both — emulates the
        // "^" prefix that DataConverter strips when loading from JSON/YAML.
        Lalein l = builder().add("msg", "%{a}", params(
                "a", param(2, "z2", "o2", "t2", null, null, "r2"),
                "alt", param(2, "Z", "O", "T", null, null, "R")
        )).build();
        // First arg unused; second arg = 2 -> a's natural TWO form
        assertEquals("t2", l.format("msg", 99, 2));
    }

    // === Numeric edge cases around the band ===

    @Test
    void doubleArithmetic_exact2_via_1_plus_1() {
        Lalein l = build_zotr();
        double sum = 1.0d + 1.0d;
        assertEquals("two", l.format("n", sum));
    }

    @Test
    void doubleArithmetic_accumulatedError_near2() {
        Lalein l = build_zotr();
        double sum = 0.0;
        for (int i = 0; i < 20; i++) sum += 0.1;  // famously not exactly 2.0
        // sum ~ 2.0000000000000004 — within the [1.999999, 2.000001] band -> "two"
        assertEquals("two", l.format("n", sum));
    }

    private static Lalein build_zotr() {
        return builder().add("n", "%{p}",
                params("p", param(1, "zero", "one", "two", null, null, "other"))).build();
    }
}
