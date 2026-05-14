package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lalein.format() with every numeric type the JDK exposes — Number subclasses,
 * boxed primitives, BigDecimal/BigInteger, AtomicInteger/AtomicLong.
 */
class NumberTypesTest {

    private Lalein basic() {
        return builder()
                .add("n", "%{p}",
                        params("p", param(1, "zero", "one", "two", null, null, "many")))
                .build();
    }

    @Test
    void int_primitive() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", 0));
        assertEquals("one", l.format("n", 1));
        assertEquals("two", l.format("n", 2));
        assertEquals("many", l.format("n", 3));
        assertEquals("many", l.format("n", 1000));
    }

    @Test
    void long_primitive() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", 0L));
        assertEquals("one", l.format("n", 1L));
        assertEquals("two", l.format("n", 2L));
        assertEquals("many", l.format("n", Long.MAX_VALUE));
    }

    @Test
    void short_byte() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", (short) 0));
        assertEquals("one", l.format("n", (short) 1));
        assertEquals("two", l.format("n", (byte) 2));
        assertEquals("many", l.format("n", (byte) 100));
    }

    @Test
    void double_exactIntegers() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", 0.0));
        assertEquals("one", l.format("n", 1.0));
        assertEquals("two", l.format("n", 2.0));
    }

    @Test
    void double_withinNaturalTolerance() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", 0.0000005));
        assertEquals("one", l.format("n", 0.9999995));
        assertEquals("one", l.format("n", 1.0000005));
        assertEquals("two", l.format("n", 1.9999995));
        assertEquals("two", l.format("n", 2.0000005));
    }

    @Test
    void double_justOutsideTolerance() {
        Lalein l = basic();
        // 1.5 is not within any natural band -> falls to OTHER ("many")
        assertEquals("many", l.format("n", 1.5));
        assertEquals("many", l.format("n", 0.5));
    }

    @Test
    void float_primitive() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", 0.0f));
        assertEquals("one", l.format("n", 1.0f));
    }

    @Test
    void negativeNumbers() {
        Lalein l = basic();
        // -1 is outside the natural ONE band (which is symmetric only around exactly 1)
        assertEquals("many", l.format("n", -1));
        assertEquals("many", l.format("n", -100));
        assertEquals("many", l.format("n", -1.5));
    }

    @Test
    void bigDecimal() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", BigDecimal.ZERO));
        assertEquals("one", l.format("n", BigDecimal.ONE));
        assertEquals("two", l.format("n", new BigDecimal("2")));
        assertEquals("two", l.format("n", new BigDecimal("2.0000005")));
        assertEquals("many", l.format("n", new BigDecimal("3.14")));
    }

    @Test
    void bigInteger() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", BigInteger.ZERO));
        assertEquals("one", l.format("n", BigInteger.ONE));
        assertEquals("two", l.format("n", BigInteger.valueOf(2)));
        assertEquals("many", l.format("n", new BigInteger("123456789012345678901234567890")));
    }

    @Test
    void atomicNumbers() {
        Lalein l = basic();
        assertEquals("zero", l.format("n", new AtomicInteger(0)));
        assertEquals("one", l.format("n", new AtomicInteger(1)));
        assertEquals("two", l.format("n", new AtomicLong(2)));
        assertEquals("many", l.format("n", new AtomicLong(42)));
    }

    @Test
    void nan_and_infinity_fallsToOther() {
        Lalein l = basic();
        assertEquals("many", l.format("n", Double.NaN));
        assertEquals("many", l.format("n", Double.POSITIVE_INFINITY));
        assertEquals("many", l.format("n", Double.NEGATIVE_INFINITY));
    }
}
