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
 * Exhaustive combinations around the value "0": every numeric type, every
 * boundary of the natural ZERO band [-0.000001, 0.000001], every combination
 * of which plural forms are present, every supported language rule, and
 * interaction with custom PluralResolvers.
 */
class ZeroValueCombinationsTest {

    // === All numeric types representing exactly 0 ===

    @Test
    void allNumericTypes_value0_returnZero() {
        Lalein l = build_zotr();
        assertEquals("zero", l.format("n", (byte) 0));
        assertEquals("zero", l.format("n", (short) 0));
        assertEquals("zero", l.format("n", 0));
        assertEquals("zero", l.format("n", 0L));
        assertEquals("zero", l.format("n", 0.0f));
        assertEquals("zero", l.format("n", 0.0d));
        assertEquals("zero", l.format("n", -0.0d));      // negative zero
        assertEquals("zero", l.format("n", BigDecimal.ZERO));
        assertEquals("zero", l.format("n", new BigDecimal("0.0")));
        assertEquals("zero", l.format("n", new BigDecimal("0.00000000000000000000")));
        assertEquals("zero", l.format("n", BigInteger.ZERO));
        assertEquals("zero", l.format("n", new AtomicInteger(0)));
        assertEquals("zero", l.format("n", new AtomicLong(0L)));
    }

    // === Natural ZERO band boundary ===

    @Test
    void boundary_inclusiveUpper_positive_0_000001() {
        Lalein l = build_zotr();
        assertEquals("zero", l.format("n", 0.000001d));
    }

    @Test
    void boundary_inclusiveLower_negative_minus_0_000001() {
        Lalein l = build_zotr();
        assertEquals("zero", l.format("n", -0.000001d));
    }

    @Test
    void boundary_justAbove_0_000002_outsideBand() {
        Lalein l = build_zotr();
        assertEquals("other", l.format("n", 0.000002d));
    }

    @Test
    void boundary_justBelow_minus_0_000002_outsideBand() {
        Lalein l = build_zotr();
        assertEquals("other", l.format("n", -0.000002d));
    }

    // === Combinations of which plural forms are present ===

    @Test
    void onlyZeroAndOther_value0_returnsZero() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", null, null, null, null, "other"))).build();
        assertEquals("zero", l.format("n", 0));
    }

    @Test
    void onlyZeroAndOther_value1_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", null, null, null, null, "other"))).build();
        assertEquals("other", l.format("n", 1));
    }

    @Test
    void onlyZeroAndOther_value2_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", null, null, null, null, "other"))).build();
        assertEquals("other", l.format("n", 2));
    }

    @Test
    void zeroNull_value0_fallsToOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "one", "two", null, null, "other"))).build();
        // 0 is natural ZERO band but z is null; no rule for English → OTHER
        assertEquals("other", l.format("n", 0));
    }

    @Test
    void zeroAndFewDefined_value0_naturalZeroWins() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "ZERO_FORM", null, null, "FEW_FORM", null, "other"))).build();
        l.setPluralResolver(n -> PluralType.FEW);
        assertEquals("ZERO_FORM", l.format("n", 0));
    }

    @Test
    void zeroNullButManyDefined_resolverReturnsMany_value0_returnsMany() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, null, null, "MANY_FORM", "other"))).build();
        l.setPluralResolver(n -> PluralType.MANY);
        assertEquals("MANY_FORM", l.format("n", 0));
    }

    @Test
    void customResolverReturnsOne_andOneDefined_butZeroAlsoDefined_value0_givesZero() {
        // Natural ZERO band beats rule for value 0
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", "one", null, null, null, "other"))).build();
        l.setPluralResolver(n -> PluralType.ONE);
        assertEquals("zero", l.format("n", 0));
    }

    // === Cross-language: value 0 ===

    @Test
    void english_value0_naturalZeroUsed() {
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("zero", l.format("n", 0));
    }

    @Test
    void english_value0_zeroNull_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", "t", null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("r", l.format("n", 0));
    }

    @Test
    void french_value0_zeroNull_ruleProducesOne() {
        // French rule: n < 1.999999 → ONE; for value 0 with z null, rule returns ONE → o
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("o", l.format("n", 0));
    }

    @Test
    void punjabi_value0_zeroNull_ruleProducesOne() {
        // pa rule: n == 0 → ONE; for value 0 with z null, rule returns ONE → o
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("pa"));
        assertEquals("o", l.format("n", 0));
    }

    @Test
    void hindi_value0_zeroNull_ruleProducesOne() {
        // hi rule: n <= 1 → ONE
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("hi"));
        assertEquals("o", l.format("n", 0));
    }

    @Test
    void portuguese_value0_zeroNull_ruleSkipsZero_returnsOther() {
        // pt rule: 0 < n < 2 → ONE (strict 0 < n, so 0 excluded). z null → OTHER.
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "o", null, null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLocale(new Locale("pt")));
        assertEquals("r", l.format("n", 0));
    }

    @Test
    void portuguese_value0_zeroDefined_zeroWins() {
        // With z defined, natural ZERO band wins over (skipped) rule
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLocale(new Locale("pt")));
        assertEquals("zero", l.format("n", 0));
    }

    // === Numeric edge cases around the band ===

    @Test
    void doubleArithmetic_subtractionToZero() {
        Lalein l = build_zotr();
        double result = 0.1 + 0.2 - 0.3;
        // result ≈ 5.55e-17 — well within ZERO tolerance
        assertEquals("zero", l.format("n", result));
    }

    @Test
    void smallestPositiveInsideBand() {
        Lalein l = build_zotr();
        assertEquals("zero", l.format("n", Double.MIN_VALUE));
    }

    @Test
    void bigDecimal_zeroWithManyZeros() {
        Lalein l = build_zotr();
        assertEquals("zero", l.format("n", new BigDecimal("0E-20")));
    }

    private static Lalein build_zotr() {
        return builder().add("n", "%{p}",
                params("p", param(1, "zero", "one", "two", null, null, "other"))).build();
    }
}
