package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class YamlEdgeCasesTest {

    @Test
    void fromResource_loads() {
        Lalein l = YamlLalein.fromResource("/Localizable.yaml");
        assertEquals("I have peaches.", l.format("peaches"));
    }

    @Test
    void fromString_simple() {
        Lalein l = YamlLalein.fromString("k: v\n");
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromString_unicode() {
        Lalein l = YamlLalein.fromString("hello: Χαίρετε\n");
        assertEquals("Χαίρετε", l.format("hello"));
    }

    @Test
    void fromReader_simple() {
        Lalein l = YamlLalein.fromReader(new StringReader("k: v\n"));
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromStream_simple() {
        Lalein l = YamlLalein.fromStream(new ByteArrayInputStream("k: v\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromFile_missing_throws() {
        File ghost = new File("/tmp/never-exists-" + System.nanoTime() + ".yaml");
        assertThrows(LaleinException.class, () -> YamlLalein.fromFile(ghost));
    }

    @Test
    void pluralForms() {
        Lalein l = YamlLalein.fromResource("/Localizable.yaml");
        assertEquals("I don't have apples.", l.format("apples", 0));
        assertEquals("I have an apple.", l.format("apples", 1));
        assertEquals("I have two apples.", l.format("apples", 2));
        assertEquals("I have 99 apples.", l.format("apples", 99));
    }

    @Test
    void nestedPlurals() {
        Lalein l = YamlLalein.fromResource("/Localizable.yaml");
        assertEquals("I have a basket with 4 oranges.", l.format("baskets_with_oranges", 1, 4));
        assertEquals("I have 2 baskets with one orange.", l.format("baskets_with_oranges", 2, 1));
    }

    @Test
    void missingHandler() {
        Lalein l = YamlLalein.fromResource("/Localizable.yaml");
        assertEquals("nothing-here", l.format("nothing-here"));
    }

    @Test
    void roundTrip() {
        Lalein original = YamlLalein.fromResource("/Localizable.yaml");
        Lalein roundTripped = YamlLalein.fromYaml(YamlLalein.toYaml(original));
        assertEquals(original, roundTripped);
    }
}
