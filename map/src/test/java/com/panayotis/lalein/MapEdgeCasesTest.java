package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge cases for the Map backend: building Lalein from hand-crafted Map structures.
 */
class MapEdgeCasesTest {

    @Test
    void emptyMap_emptyLalein() {
        Lalein l = MapLalein.fromMap(new LinkedHashMap<>());
        assertEquals("ghost", l.format("ghost"));
    }

    @Test
    void simpleStringValues() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("greeting", "Hello");
        data.put("farewell", "Bye");
        Lalein l = MapLalein.fromMap(data);
        assertEquals("Hello", l.format("greeting"));
        assertEquals("Bye", l.format("farewell"));
    }

    @Test
    void singleParameterAsNestedMap() {
        Map<String, Object> apples = new LinkedHashMap<>();
        apples.put("z", "no apples");
        apples.put("o", "one apple");
        apples.put("r", "%d apples");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apples", apples);
        Lalein l = MapLalein.fromMap(data);
        assertEquals("no apples", l.format("apples", 0));
        assertEquals("one apple", l.format("apples", 1));
        assertEquals("5 apples", l.format("apples", 5));
    }

    @Test
    void roundTrip_preservesStructure() {
        Map<String, Object> apples = new LinkedHashMap<>();
        apples.put("z", "none");
        apples.put("o", "one");
        apples.put("r", "%d");
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("hello", "Hi");
        original.put("apples", apples);

        Lalein l = MapLalein.fromMap(original);
        Map<String, Object> reproduced = MapLalein.toMap(l);
        assertEquals(original, reproduced);
        // round-trip stability
        assertEquals(l, MapLalein.fromMap(reproduced));
    }

    @Test
    void nonPluralKeysAreStoredAsCustomSelectors() {
        // Non-CLDR keys are no longer rejected — they become select-mode lookups
        // (used for gender, formality, etc.). Plural behaviour for Number args
        // is unchanged.
        Map<String, Object> apples = new LinkedHashMap<>();
        apples.put("o", "one apple");
        apples.put("custom_key", "boom");
        apples.put("r", "many apples");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apples", apples);
        Lalein l = MapLalein.fromMap(data);

        assertEquals("one apple",   l.format("apples", 1));
        assertEquals("many apples", l.format("apples", 5));
        assertEquals("boom",        l.format("apples", "custom_key"));
        assertEquals("many apples", l.format("apples", "unknown")); // fallback to r
    }
}
