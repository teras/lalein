package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameter construction and resolve() semantics in isolation.
 */
class ParameterTest {

    private static final PluralResolver NEVER = n -> null;

    @Test
    void constructor_negativeIndex_throws() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> new Parameter(-1, null, null, null, null, null, "x"));
    }

    @Test
    void constructor_zeroIndex_throws() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> new Parameter(0, null, null, null, null, null, "x"));
    }

    @Test
    void constructor_nullOther_becomesEmptyString() {
        Parameter p = new Parameter(1, null, null, null, null, null, null);
        assertEquals("", p.resolve(NEVER, new Object[]{5}));
    }

    @Test
    void resolve_nullArgsArray_throws() {
        Parameter p = param(1, null, "one", null, null, null, "other");
        assertThrows(LaleinException.class, () -> p.resolve(NEVER, null));
    }

    @Test
    void resolve_emptyArgsArray_throws() {
        Parameter p = param(1, null, "one", null, null, null, "other");
        assertThrows(LaleinException.class, () -> p.resolve(NEVER, new Object[0]));
    }

    @Test
    void resolve_argsTooShortForIndex_throws() {
        Parameter p = param(3, null, "one", null, null, null, "other");
        LaleinException ex = assertThrows(LaleinException.class,
                () -> p.resolve(NEVER, new Object[]{1, 2}));
        assertTrue(ex.getMessage().contains("#3"));
        assertTrue(ex.getMessage().contains("2 parameters are given"));
    }

    @Test
    void resolve_singleArgMessageUsesSingular() {
        Parameter p = param(2, null, "one", null, null, null, "other");
        LaleinException ex = assertThrows(LaleinException.class,
                () -> p.resolve(NEVER, new Object[]{1}));
        assertTrue(ex.getMessage().contains("1 parameter is given"));
    }

    @Test
    void resolve_nullArg_throws() {
        Parameter p = param(1, null, "one", null, null, null, "other");
        LaleinException ex = assertThrows(LaleinException.class,
                () -> p.resolve(NEVER, new Object[]{null}));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void resolve_nonNumberArg_throws() {
        Parameter p = param(1, null, "one", null, null, null, "other");
        LaleinException ex = assertThrows(LaleinException.class,
                () -> p.resolve(NEVER, new Object[]{"five"}));
        assertTrue(ex.getMessage().contains("String"));
    }

    @Test
    void resolve_naturalZero_whenZeroDefined() {
        Parameter p = param(1, "no apples", "one apple", null, null, null, "%d apples");
        assertEquals("no apples", p.resolve(NEVER, new Object[]{0}));
    }

    @Test
    void resolve_naturalZero_fallsToOther_whenZeroAbsent() {
        Parameter p = param(1, null, "one apple", null, null, null, "%d apples");
        assertEquals("%d apples", p.resolve(NEVER, new Object[]{0}));
    }

    @Test
    void resolve_naturalOne() {
        Parameter p = param(1, null, "one apple", null, null, null, "%d apples");
        assertEquals("one apple", p.resolve(NEVER, new Object[]{1}));
    }

    @Test
    void resolve_naturalTwo() {
        Parameter p = param(1, null, null, "two apples", null, null, "%d apples");
        assertEquals("two apples", p.resolve(NEVER, new Object[]{2}));
    }

    @Test
    void resolve_naturalTwo_fallsToOther_whenTwoAbsent() {
        Parameter p = param(1, null, null, null, null, null, "%d apples");
        assertEquals("%d apples", p.resolve(NEVER, new Object[]{2}));
    }

    @Test
    void resolve_picksSecondArgWhenIndexIsTwo() {
        Parameter p = param(2, "no oranges", "one orange", null, null, null, "%2$d oranges");
        assertEquals("no oranges", p.resolve(NEVER, new Object[]{5, 0}));
        assertEquals("one orange", p.resolve(NEVER, new Object[]{5, 1}));
        assertEquals("%2$d oranges", p.resolve(NEVER, new Object[]{5, 7}));
    }

    @Test
    void resolve_customResolverProducesFew() {
        PluralResolver fewForThree = n -> n.intValue() == 3 ? PluralType.FEW : null;
        Parameter p = param(1, "z", "o", "t", "FEW", "m", "other");
        assertEquals("FEW", p.resolve(fewForThree, new Object[]{3}));
    }

    @Test
    void resolve_customResolverProducesMany() {
        PluralResolver manyForFive = n -> n.intValue() == 5 ? PluralType.MANY : null;
        Parameter p = param(1, "z", "o", "t", "f", "MANY", "other");
        assertEquals("MANY", p.resolve(manyForFive, new Object[]{5}));
    }

    @Test
    void resolve_resolverReturnsNull_fallsToOther() {
        Parameter p = param(1, "z", "o", null, null, null, "DEFAULT");
        assertEquals("DEFAULT", p.resolve(NEVER, new Object[]{7}));
    }

    @Test
    void resolve_resolverReturnsCategoryButThatFormIsAbsent_fallsToOther() {
        PluralResolver alwaysFew = n -> PluralType.FEW;
        Parameter p = param(1, "z", "o", null, null, null, "FALLBACK");
        assertEquals("FALLBACK", p.resolve(alwaysFew, new Object[]{42}));
    }

    @Test
    void resolve_naturalBeatsRuleBasedAtExactBoundary() {
        // resolver says ONE, but natural ZERO should take priority because 0 is in the ZERO band
        PluralResolver alwaysOne = n -> PluralType.ONE;
        Parameter p = param(1, "zeroForm", "oneForm", null, null, null, "other");
        assertEquals("zeroForm", p.resolve(alwaysOne, new Object[]{0}));
    }

    @Test
    void resolve_zeroBandIncludesNegativeTinyValues() {
        // -0.0000005 is within ZERO band (>= -0.000001)
        Parameter p = param(1, "z", "o", null, null, null, "other");
        assertEquals("z", p.resolve(NEVER, new Object[]{-0.0000005d}));
    }

    @Test
    void resolve_outsideAllNaturalBands_andNoRule_goesToOther() {
        Parameter p = param(1, "z", "o", "t", null, null, "OTHER");
        assertEquals("OTHER", p.resolve(NEVER, new Object[]{1.5d}));
        assertEquals("OTHER", p.resolve(NEVER, new Object[]{2.5d}));
        assertEquals("OTHER", p.resolve(NEVER, new Object[]{-1d}));
        assertEquals("OTHER", p.resolve(NEVER, new Object[]{100}));
    }
}
