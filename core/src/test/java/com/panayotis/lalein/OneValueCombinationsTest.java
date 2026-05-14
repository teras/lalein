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
 * Exhaustive combinations around the value "1": every numeric type, every
 * boundary of the natural ONE band [0.999999, 1.000001], every combination
 * of which plural forms are present, every supported language rule, and
 * interaction with custom PluralResolvers.
 */
class OneValueCombinationsTest {

    // === All numeric types representing exactly 1 ===

    @Test
    void allNumericTypes_value1_returnOne() {
        Lalein l = build_zotr();
        assertEquals("one", l.format("n", (byte) 1));
        assertEquals("one", l.format("n", (short) 1));
        assertEquals("one", l.format("n", 1));
        assertEquals("one", l.format("n", 1L));
        assertEquals("one", l.format("n", 1.0f));
        assertEquals("one", l.format("n", 1.0d));
        assertEquals("one", l.format("n", new BigDecimal("1")));
        assertEquals("one", l.format("n", new BigDecimal("1.0")));
        assertEquals("one", l.format("n", new BigDecimal("1.00000000000000000000")));
        assertEquals("one", l.format("n", BigInteger.ONE));
        assertEquals("one", l.format("n", new AtomicInteger(1)));
        assertEquals("one", l.format("n", new AtomicLong(1L)));
    }

    // === Natural ONE band boundary ===

    @Test
    void boundary_inclusiveLower_0_999999() {
        Lalein l = build_zotr();
        assertEquals("one", l.format("n", 0.999999d));
    }

    @Test
    void boundary_inclusiveUpper_1_000001() {
        Lalein l = build_zotr();
        assertEquals("one", l.format("n", 1.000001d));
    }

    @Test
    void boundary_justBelow_0_999998_outsideBand() {
        Lalein l = build_zotr();
        assertEquals("other", l.format("n", 0.999998d));
    }

    @Test
    void boundary_justAbove_1_000002_outsideBand() {
        Lalein l = build_zotr();
        assertEquals("other", l.format("n", 1.000002d));
    }

    @Test
    void negativeOne_notInOneBand() {
        Lalein l = build_zotr();
        assertEquals("other", l.format("n", -1));
        assertEquals("other", l.format("n", -1.0d));
        assertEquals("other", l.format("n", new BigDecimal("-1")));
    }

    // === Combinations of which plural forms are present ===

    @Test
    void onlyOneAndOther_value1_returnsOne() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "one", null, null, null, "other"))).build();
        assertEquals("one", l.format("n", 1));
    }

    @Test
    void onlyOneAndOther_value3_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "one", null, null, null, "other"))).build();
        assertEquals("other", l.format("n", 3));
    }

    @Test
    void onlyOneAndOther_value0_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "one", null, null, null, "other"))).build();
        // value 0 hits natural ZERO band, but z is null → falls to rule (null) → OTHER
        assertEquals("other", l.format("n", 0));
    }

    @Test
    void onlyOneAndOther_value2_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "one", null, null, null, "other"))).build();
        // value 2 hits natural TWO band, but t is null → falls to rule → OTHER
        assertEquals("other", l.format("n", 2));
    }

    @Test
    void oneNull_value1_fallsToOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", null, "two", null, null, "other"))).build();
        assertEquals("other", l.format("n", 1));
    }

    @Test
    void oneAndFewDefined_value1_naturalOneWins() {
        // resolver claiming FEW for 1, but natural ONE band kicks in first
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, "ONE_FORM", null, "FEW_FORM", null, "other"))).build();
        l.setPluralResolver(n -> PluralType.FEW);
        assertEquals("ONE_FORM", l.format("n", 1));
    }

    @Test
    void oneNullButManyDefined_resolverReturnsMany_value1_returnsMany() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, null, null, null, null, "MANY_FORM", "other"))).build();
        l.setPluralResolver(n -> PluralType.MANY);
        assertEquals("MANY_FORM", l.format("n", 1));
    }

    @Test
    void customResolverReturnsZero_andZeroDefined_butOneAlsoDefined_value1_givesOne() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "zero", "one", null, null, null, "other"))).build();
        l.setPluralResolver(n -> PluralType.ZERO);
        // Natural ONE band overrides rule
        assertEquals("one", l.format("n", 1));
    }

    // === Cross-language: value 1 ===

    @Test
    void english_value1_naturalOneUsed() {
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("one", l.format("n", 1));
    }

    @Test
    void english_value1_oneNull_returnsOther() {
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "z", null, "t", null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("r", l.format("n", 1));
    }

    @Test
    void french_value1_naturalOneUsed() {
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("one", l.format("n", 1));
    }

    @Test
    void french_value1_oneNull_ruleFiresButFallsThrough() {
        // French rule says ONE for 1 (1 < 1.999999), but o is null → falls through to OTHER
        Lalein l = builder().add("n", "%{p}",
                params("p", param(1, "z", null, "t", null, null, "r"))).build();
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("r", l.format("n", 1));
    }

    @Test
    void hindi_value1_naturalOneWins() {
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("hi"));
        assertEquals("one", l.format("n", 1));
    }

    @Test
    void punjabi_value1_naturalOneWins() {
        // pa rule fires only on 0, so for 1 we rely on natural ONE.
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLanguage("pa"));
        assertEquals("one", l.format("n", 1));
    }

    @Test
    void portuguese_value1_naturalOneWins() {
        Lalein l = build_zotr();
        l.setPluralResolver(PluralResolvers.usingLocale(new Locale("pt")));
        assertEquals("one", l.format("n", 1));
    }

    // === Numeric edge cases around the band ===

    @Test
    void doubleArithmetic_accumulatedError_near1() {
        Lalein l = build_zotr();
        double sum = 0.0;
        for (int i = 0; i < 10; i++) sum += 0.1;
        // 0.1 × 10 ≈ 0.9999999999999999 — within ONE band → "one"
        assertEquals("one", l.format("n", sum));
    }

    @Test
    void bigDecimal_oneWithManyZeros() {
        Lalein l = build_zotr();
        // 1.0 expressed with many fractional zeros must still be ONE
        assertEquals("one", l.format("n", new BigDecimal("1.0000000000000001")));
    }

    private static Lalein build_zotr() {
        return builder().add("n", "%{p}",
                params("p", param(1, "zero", "one", "two", null, null, "other"))).build();
    }
}
