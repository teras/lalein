package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class FluentLaleinTest {

    private static Lalein loadCanonical() {
        return FluentLalein.fromResource("/Localizable.ftl");
    }

    @Test
    void readsSimpleStringEntry() {
        Lalein lalein = loadCanonical();
        assertEquals("I have peaches.", lalein.format("peaches"));
    }

    @Test
    void readsSingleParameterPlurals_apples() {
        Lalein lalein = loadCanonical();
        assertEquals("I don't have apples.", lalein.format("apples", 0));
        assertEquals("I have an apple.",    lalein.format("apples", 1));
        assertEquals("I have two apples.",  lalein.format("apples", 2));
        assertEquals("I have 27 apples.",   lalein.format("apples", 27));
    }

    @Test
    void readsNestedSelectors_basketsWithOranges() {
        // Fluent uses 3 distinct variables: $baskets, $oranges_zero_basket, $oranges.
        // Lalein assigns arg indices 1, 2, 3 by order of first appearance.
        Lalein lalein = loadCanonical();
        // basket=0 branch reads $oranges_zero_basket from arg 2
        assertEquals("I don't have a basket or an orange.",         lalein.format("baskets_with_oranges", 0, 0, 99));
        assertEquals("I don't have a basket but I have an orange.", lalein.format("baskets_with_oranges", 0, 1, 99));
        assertEquals("I don't have a basket but I have 10 oranges.", lalein.format("baskets_with_oranges", 0, 10, 99));
        // basket=other branch reads $oranges from arg 3
        assertEquals("I have 1 baskets without oranges.",            lalein.format("baskets_with_oranges", 1, 99, 0));
        assertEquals("I have 1 baskets with one orange.",            lalein.format("baskets_with_oranges", 1, 99, 1));
        assertEquals("I have 7 baskets with 9 oranges.",             lalein.format("baskets_with_oranges", 7, 99, 9));
    }

    @Test
    void missingHandler_returnedAsIs() {
        Lalein lalein = loadCanonical();
        assertEquals("This does not exist", lalein.format("This does not exist"));
    }

    @Test
    void fromString_simple() {
        Lalein lalein = FluentLalein.fromString("hi = Hello\n");
        assertEquals("Hello", lalein.format("hi"));
    }

    @Test
    void fromString_unicode() {
        Lalein lalein = FluentLalein.fromString("hi = Χαίρετε\n");
        assertEquals("Χαίρετε", lalein.format("hi"));
    }

    @Test
    void fromString_singleArgPlural() {
        String ftl = "apples = { $count ->\n" +
                "    [zero]  No apples.\n" +
                "    [one]   One apple.\n" +
                "   *[other] { $count } apples.\n" +
                "}\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        assertEquals("No apples.", lalein.format("apples", 0));
        assertEquals("One apple.", lalein.format("apples", 1));
        assertEquals("5 apples.", lalein.format("apples", 5));
    }

    @Test
    void fromString_commentsIgnored() {
        String ftl = "# top comment\n" +
                "k = v\n" +
                "# trailing comment\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        assertEquals("v", lalein.format("k"));
    }

    @Test
    void fromString_blankLinesAllowed() {
        String ftl = "\n\nk1 = v1\n\n\nk2 = v2\n\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        assertEquals("v1", lalein.format("k1"));
        assertEquals("v2", lalein.format("k2"));
    }

    @Test
    void fromStream_simple() {
        Lalein lalein = FluentLalein.fromStream(
                new ByteArrayInputStream("k = v\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("v", lalein.format("k"));
    }

    @Test
    void fromReader_simple() {
        Lalein lalein = FluentLalein.fromReader(new StringReader("k = v\n"));
        assertEquals("v", lalein.format("k"));
    }

    @Test
    void fromFile_missing_throws() {
        File ghost = new File("/tmp/never-exists-" + System.nanoTime() + ".ftl");
        assertThrows(LaleinException.class, () -> FluentLalein.fromFile(ghost));
    }

    @Test
    void fromResource_missing_throws() {
        assertThrows(LaleinException.class, () -> FluentLalein.fromResource("/nope.ftl"));
    }

    // === Write direction ===

    @Test
    void toString_simpleTranslation() {
        Lalein lalein = FluentLalein.fromString("hi = Hello\n");
        String out = FluentLalein.toString(lalein);
        assertTrue(out.contains("hi = Hello"));
    }

    @Test
    void toString_singleArgPlural() {
        String ftl = "apples = { $count ->\n" +
                "    [one]   1 apple\n" +
                "   *[other] { $count } apples\n" +
                "}\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        String out = FluentLalein.toString(lalein);
        assertTrue(out.contains("$count"));
        assertTrue(out.contains("[one]"));
        assertTrue(out.contains("*[other]"));
    }

    @Test
    void roundTrip_apples_preservesBehaviour() {
        Lalein original = loadCanonical();
        String ftl = FluentLalein.toString(original);
        Lalein reread = FluentLalein.fromString(ftl);
        assertEquals("I don't have apples.", reread.format("apples", 0));
        assertEquals("I have an apple.",     reread.format("apples", 1));
        assertEquals("I have two apples.",   reread.format("apples", 2));
        assertEquals("I have 42 apples.",    reread.format("apples", 42));
    }

    @Test
    void roundTrip_basketsWithOranges_preservesBehaviour() {
        Lalein original = loadCanonical();
        String ftl = FluentLalein.toString(original);
        Lalein reread = FluentLalein.fromString(ftl);
        // Same 3-arg semantics
        assertEquals("I don't have a basket or an orange.", reread.format("baskets_with_oranges", 0, 0, 99));
        assertEquals("I have 7 baskets with 9 oranges.",    reread.format("baskets_with_oranges", 7, 99, 9));
        // Second cycle stability
        String again = FluentLalein.toString(reread);
        Lalein third = FluentLalein.fromString(again);
        assertEquals("I have 3 baskets with 5 oranges.",    third.format("baskets_with_oranges", 3, 99, 5));
    }

    @Test
    void variantOrderInWriter_followsCldrOrder() {
        // Even if Lalein has all 6 forms defined, writer emits them in z/o/t/f/m/r order.
        String ftl = "x = { $n ->\n" +
                "    [zero]  Z\n" +
                "    [one]   O\n" +
                "    [two]   T\n" +
                "    [few]   F\n" +
                "    [many]  M\n" +
                "   *[other] R\n" +
                "}\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        String out = FluentLalein.toString(lalein);
        int izero = out.indexOf("[zero]");
        int ione = out.indexOf("[one]");
        int itwo = out.indexOf("[two]");
        int ifew = out.indexOf("[few]");
        int imany = out.indexOf("[many]");
        int iother = out.indexOf("[other]");
        assertTrue(izero >= 0 && ione > izero && itwo > ione && ifew > itwo && imany > ifew && iother > imany,
                "CLDR variant order in writer output");
    }

    @Test
    void numericValueRefInsideVariant_resolvesToOwnArgument() {
        // Demonstrates that "%count" in Fluent's variant pattern becomes the formatted
        // value of the selector argument, not a reference to another parameter.
        String ftl = "n = { $count ->\n" +
                "    [one]   exactly one\n" +
                "   *[other] count is { $count }\n" +
                "}\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        assertEquals("exactly one",     lalein.format("n", 1));
        assertEquals("count is 5",      lalein.format("n", 5));
        assertEquals("count is 42",     lalein.format("n", 42));
    }
}
