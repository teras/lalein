package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JsonEdgeCasesTest {

    @Test
    void fromResource_loads() {
        Lalein l = JsonLalein.fromResource("/Localizable.json");
        assertEquals("I have peaches.", l.format("peaches"));
    }

    @Test
    void fromResource_missing_throws() {
        assertThrows(Exception.class, () -> JsonLalein.fromResource("/missing.json"));
    }

    @Test
    void fromString_simple() {
        Lalein l = JsonLalein.fromString("{\"k\":\"v\"}");
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromString_emptyObject() {
        Lalein l = JsonLalein.fromString("{}");
        assertEquals("ghost", l.format("ghost"));
    }

    @Test
    void fromString_unicodeNative() {
        Lalein l = JsonLalein.fromString("{\"hello\":\"Χαίρετε\"}");
        assertEquals("Χαίρετε", l.format("hello"));
    }

    @Test
    void fromString_unicodeEscape() {
        Lalein l = JsonLalein.fromString("{\"hello\":\"\\u03a7\\u03b1\\u03af\\u03c1\\u03b5\\u03c4\\u03b5\"}");
        assertEquals("Χαίρετε", l.format("hello"));
    }

    @Test
    void fromString_malformed_throws() {
        assertThrows(Exception.class, () -> JsonLalein.fromString("{ broken"));
    }

    @Test
    void fromStream_loads() {
        Lalein l = JsonLalein.fromStream(new ByteArrayInputStream("{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromReader_loads() {
        Lalein l = JsonLalein.fromReader(new StringReader("{\"k\":\"v\"}"));
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromFile_missing_throws() {
        File ghost = new File("/tmp/never-exists-" + System.nanoTime() + ".json");
        assertThrows(LaleinException.class, () -> JsonLalein.fromFile(ghost));
    }

    @Test
    void roundTrip_preservesAll() throws IOException {
        JsonObject original = Json.parse(new InputStreamReader(
                JsonEdgeCasesTest.class.getResourceAsStream("/Localizable.json"),
                StandardCharsets.UTF_8)).asObject();
        Lalein lalein = JsonLalein.fromJson(original);
        JsonObject produced = JsonLalein.toJson(lalein);
        assertEquals(original, produced);
        // stability across two cycles
        Lalein second = JsonLalein.fromJson(produced);
        assertEquals(lalein, second);
    }

    @Test
    void pluralForms() {
        Lalein l = JsonLalein.fromResource("/Localizable.json");
        assertEquals("I don't have apples.", l.format("apples", 0));
        assertEquals("I have an apple.", l.format("apples", 1));
        assertEquals("I have two apples.", l.format("apples", 2));
        assertEquals("I have 42 apples.", l.format("apples", 42));
    }

    @Test
    void nestedTwoArgs() {
        Lalein l = JsonLalein.fromResource("/Localizable.json");
        assertEquals("I have 3 baskets with 5 oranges.", l.format("baskets_with_oranges", 3, 5));
    }

    @Test
    void wrongArgType_throws() {
        Lalein l = JsonLalein.fromResource("/Localizable.json");
        assertThrows(LaleinException.class, () -> l.format("apples", "text"));
    }
}
