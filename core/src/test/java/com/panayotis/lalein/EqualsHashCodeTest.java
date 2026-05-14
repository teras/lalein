package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * equals()/hashCode() contracts for Lalein, Translation, Parameter.
 */
class EqualsHashCodeTest {

    @Test
    void parameter_sameContent_equal() {
        Parameter a = param(1, "z", "o", "t", "f", "m", "r");
        Parameter b = param(1, "z", "o", "t", "f", "m", "r");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void parameter_differentIndex_notEqual() {
        assertNotEquals(
                param(1, "z", "o", "t", "f", "m", "r"),
                param(2, "z", "o", "t", "f", "m", "r"));
    }

    @Test
    void parameter_differentForm_notEqual() {
        assertNotEquals(
                param(1, "z", "o", "t", "f", "m", "r"),
                param(1, "z", "ONE!", "t", "f", "m", "r"));
    }

    @Test
    void parameter_notEqual_toNullOrOther() {
        Parameter a = param(1, "z", "o", "t", "f", "m", "r");
        assertNotEquals(a, null);
        assertNotEquals(a, "string");
    }

    @Test
    void parameter_reflexive() {
        Parameter a = param(1, "z", "o", "t", "f", "m", "r");
        assertEquals(a, a);
    }

    @Test
    void translation_simpleEqual() {
        assertEquals(new Translation("hello", null), new Translation("hello", null));
    }

    @Test
    void translation_differentFormat_notEqual() {
        assertNotEquals(new Translation("a", null), new Translation("b", null));
    }

    @Test
    void translation_paramsEqual() {
        Map<String, Parameter> p1 = params("x", param(1, null, "o", null, null, null, "r"));
        Map<String, Parameter> p2 = params("x", param(1, null, "o", null, null, null, "r"));
        assertEquals(new Translation("%{x}", p1), new Translation("%{x}", p2));
    }

    @Test
    void translation_paramKeyOrderMatters_forEquals() {
        // The current equals() compares LinkedHashMap key order via an ArrayList(keySet()) check.
        Map<String, Parameter> p1 = new LinkedHashMap<>();
        p1.put("a", param(1, null, "o", null, null, null, "r"));
        p1.put("b", param(2, null, "o", null, null, null, "r"));
        Map<String, Parameter> p2 = new LinkedHashMap<>();
        p2.put("b", param(2, null, "o", null, null, null, "r"));
        p2.put("a", param(1, null, "o", null, null, null, "r"));
        Translation t1 = new Translation("%{a}", p1);
        Translation t2 = new Translation("%{a}", p2);
        assertNotEquals(t1, t2);
    }

    @Test
    void translation_reflexive() {
        Translation t = new Translation("x", null);
        assertEquals(t, t);
    }

    @Test
    void translation_notEqualToOtherType() {
        Translation t = new Translation("x", null);
        assertNotEquals(t, null);
        assertNotEquals(t, "x");
    }

    @Test
    void lalein_sameRegistry_equal() {
        Lalein a = builder().add("hello", "Hello").build();
        Lalein b = builder().add("hello", "Hello").build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void lalein_differentRegistry_notEqual() {
        Lalein a = builder().add("hello", "Hello").build();
        Lalein b = builder().add("hello", "World").build();
        assertNotEquals(a, b);
    }

    @Test
    void lalein_extraEntry_notEqual() {
        Lalein a = builder().add("hello", "Hi").build();
        Lalein b = builder().add("hello", "Hi").add("bye", "Bye").build();
        assertNotEquals(a, b);
    }

    @Test
    void lalein_reflexive() {
        Lalein a = builder().add("hello", "Hi").build();
        assertEquals(a, a);
    }

    @Test
    void lalein_notEqualToOtherType() {
        Lalein a = builder().add("hello", "Hi").build();
        assertNotEquals(a, null);
        assertNotEquals(a, "Hi");
    }
}
