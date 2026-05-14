package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge cases for the Properties backend: every loader entry-point, unicode,
 * error paths, and round-trip with edge data.
 */
class PropertiesEdgeCasesTest {

    @Test
    void fromResource_loadsExistingFile() {
        Lalein l = PropertiesLalein.fromResource("/Localizable.properties");
        assertEquals("I have peaches.", l.format("peaches"));
    }

    @Test
    void fromResource_missingResource_throws() {
        // getResourceAsStream returns null; try-with-resources NPEs which surfaces as ?
        // Currently the code calls is.close() on null inside try-with-resources block.
        // Either NullPointerException or LaleinException is acceptable failure.
        assertThrows(Exception.class, () -> PropertiesLalein.fromResource("/does-not-exist.properties"));
    }

    @Test
    void fromString_simple() {
        Lalein l = PropertiesLalein.fromString("greeting=Hello\nfarewell=Bye\n");
        assertEquals("Hello", l.format("greeting"));
        assertEquals("Bye", l.format("farewell"));
    }

    @Test
    void fromString_empty_emptyLalein() {
        Lalein l = PropertiesLalein.fromString("");
        // Unknown keys come back as themselves
        assertEquals("anything", l.format("anything"));
    }

    @Test
    void fromString_withComments_commentsIgnored() {
        Lalein l = PropertiesLalein.fromString("# this is a comment\n! also a comment\ngreeting=Hi\n");
        assertEquals("Hi", l.format("greeting"));
    }

    @Test
    void fromString_unicode_viaEscape() {
        // Properties text format uses \\uNNNN for non-ASCII characters
        Lalein l = PropertiesLalein.fromString("hello=\\u03a7\\u03b1\\u03af\\u03c1\\u03b5\\u03c4\\u03b5");
        assertEquals("Χαίρετε", l.format("hello"));
    }

    @Test
    void fromReader_simple() {
        Lalein l = PropertiesLalein.fromReader(new StringReader("k=v"));
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromStream_simple() {
        Lalein l = PropertiesLalein.fromStream(new ByteArrayInputStream("k=v".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("v", l.format("k"));
    }

    @Test
    void fromFile_missingFile_throws() {
        File ghost = new File("/tmp/never-exists-" + System.nanoTime() + ".properties");
        assertThrows(LaleinException.class, () -> PropertiesLalein.fromFile(ghost));
    }

    @Test
    void fromProperties_directBuild() {
        Properties p = new Properties();
        p.setProperty("k", "v");
        Lalein l = PropertiesLalein.fromProperties(p);
        assertEquals("v", l.format("k"));
    }

    @Test
    void roundTrip_preservesAllKeys() throws IOException {
        Properties original = new Properties();
        original.load(PropertiesEdgeCasesTest.class.getResourceAsStream("/Localizable.properties"));
        Lalein lalein = PropertiesLalein.fromProperties(original);
        Properties produced = PropertiesLalein.toProperties(lalein);
        assertEquals(original, produced);
        // round-trip stability — two passes equal
        Lalein second = PropertiesLalein.fromProperties(produced);
        assertEquals(lalein, second);
    }

    @Test
    void pluralForms_zeroOneTwoOther() {
        Lalein l = PropertiesLalein.fromResource("/Localizable.properties");
        assertEquals("I don't have apples.", l.format("apples", 0));
        assertEquals("I have an apple.", l.format("apples", 1));
        assertEquals("I have two apples.", l.format("apples", 2));
        assertEquals("I have 5 apples.", l.format("apples", 5));
    }

    @Test
    void nestedPluralForms() {
        Lalein l = PropertiesLalein.fromResource("/Localizable.properties");
        assertEquals("I don't have a basket or an orange.", l.format("baskets_with_oranges", 0, 0));
        assertEquals("I have a basket with 3 oranges.", l.format("baskets_with_oranges", 1, 3));
        assertEquals("I have 5 baskets with 7 oranges.", l.format("baskets_with_oranges", 5, 7));
    }

    @Test
    void missingHandler_returnedAsIs() {
        Lalein l = PropertiesLalein.fromResource("/Localizable.properties");
        assertEquals("ghost-key", l.format("ghost-key"));
    }

    @Test
    void wrongArgType_throws() {
        Lalein l = PropertiesLalein.fromResource("/Localizable.properties");
        assertThrows(LaleinException.class, () -> l.format("apples", "five"));
    }

    @Test
    void notEnoughArgs_throws() {
        Lalein l = PropertiesLalein.fromResource("/Localizable.properties");
        assertThrows(LaleinException.class, () -> l.format("apples"));
    }
}
