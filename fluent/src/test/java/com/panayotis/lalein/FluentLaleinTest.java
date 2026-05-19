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
        // Writer emits numeric-literal selectors for the exact-match slots.
        assertTrue(out.contains("[1]"));
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
        // Exact-match slots (zero/one/two) are emitted as numeric-literal selectors.
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
        int izero = out.indexOf("[0]");
        int ione = out.indexOf("[1]");
        int itwo = out.indexOf("[2]");
        int ifew = out.indexOf("[few]");
        int imany = out.indexOf("[many]");
        int iother = out.indexOf("[other]");
        assertTrue(izero >= 0 && ione > izero && itwo > ione && ifew > itwo && imany > ifew && iother > imany,
                "CLDR variant order in writer output");
    }

    // === Select-mode round-trip ===

    @Test
    void readsSelectMode_gender() {
        String ftl = "liked_post = { $gender ->\n" +
                "    [female] She liked your post\n" +
                "    [male]   He liked your post\n" +
                "   *[other]  They liked your post\n" +
                "}\n";
        Lalein lalein = FluentLalein.fromString(ftl);
        assertEquals("She liked your post",  lalein.format("liked_post", "female"));
        assertEquals("He liked your post",   lalein.format("liked_post", "male"));
        assertEquals("They liked your post", lalein.format("liked_post", "other"));
        assertEquals("They liked your post", lalein.format("liked_post", "non-binary"));
    }

    @Test
    void roundTrip_selectMode_gender_preservesBehaviour() {
        String ftl = "liked_post = { $gender ->\n" +
                "    [female] She liked your post\n" +
                "    [male]   He liked your post\n" +
                "   *[other]  They liked your post\n" +
                "}\n";
        Lalein original = FluentLalein.fromString(ftl);
        String emitted = FluentLalein.toString(original);
        // Custom keys must survive a write
        assertTrue(emitted.contains("[female]"), "custom [female] preserved: " + emitted);
        assertTrue(emitted.contains("[male]"),   "custom [male] preserved: " + emitted);
        assertTrue(emitted.contains("*[other]"), "default *[other] preserved: " + emitted);
        Lalein reread = FluentLalein.fromString(emitted);
        assertEquals("She liked your post",  reread.format("liked_post", "female"));
        assertEquals("He liked your post",   reread.format("liked_post", "male"));
        assertEquals("They liked your post", reread.format("liked_post", "other"));
        // Second cycle stability
        String again = FluentLalein.toString(reread);
        Lalein third = FluentLalein.fromString(again);
        assertEquals("He liked your post",   third.format("liked_post", "male"));
        assertEquals("They liked your post", third.format("liked_post", "unknown"));
    }

    @Test
    void roundTrip_selectMode_formalityWithEcho() {
        // Variant body references the selector itself — must round-trip as { $register }.
        String ftl = "greeting = { $register ->\n" +
                "    [formal]   Good day, Mr. { $register }\n" +
                "    [informal] Hey { $register }\n" +
                "   *[other]    Hello { $register }\n" +
                "}\n";
        Lalein original = FluentLalein.fromString(ftl);
        // Sanity check on input behavior
        assertEquals("Good day, Mr. formal", original.format("greeting", "formal"));
        assertEquals("Hey informal",         original.format("greeting", "informal"));
        assertEquals("Hello casual",         original.format("greeting", "casual"));
        // Round-trip
        String emitted = FluentLalein.toString(original);
        assertTrue(emitted.contains("[formal]"));
        assertTrue(emitted.contains("[informal]"));
        Lalein reread = FluentLalein.fromString(emitted);
        assertEquals("Good day, Mr. formal", reread.format("greeting", "formal"));
        assertEquals("Hey informal",         reread.format("greeting", "informal"));
        assertEquals("Hello casual",         reread.format("greeting", "casual"));
    }

    @Test
    void roundTrip_nestedSelectInPlural() {
        // CLDR plural at the outer level, gender select-mode nested inside one branch.
        // Each Fluent selector variable produces one Lalein Parameter; we keep the gender
        // nest inside the *[other] branch only so the variable name is unique.
        String ftl = "messages = { $count ->\n" +
                "    [zero]  No new messages.\n" +
                "    [one]   1 new message.\n" +
                "   *[other] { $gender ->\n" +
                "        [female] She sent you { $count } messages.\n" +
                "        [male]   He sent you { $count } messages.\n" +
                "       *[other]  They sent you { $count } messages.\n" +
                "    }\n" +
                "}\n";
        Lalein original = FluentLalein.fromString(ftl);
        // Arg 1: count (numeric), Arg 2: gender (string).
        assertEquals("No new messages.",          original.format("messages", 0, "female"));
        assertEquals("1 new message.",            original.format("messages", 1, "female"));
        assertEquals("He sent you 5 messages.",   original.format("messages", 5, "male"));
        assertEquals("She sent you 12 messages.", original.format("messages", 12, "female"));
        assertEquals("They sent you 7 messages.", original.format("messages", 7, "unknown"));
        // Round-trip preserves behaviour
        String emitted = FluentLalein.toString(original);
        Lalein reread = FluentLalein.fromString(emitted);
        assertEquals("No new messages.",          reread.format("messages", 0, "female"));
        assertEquals("1 new message.",            reread.format("messages", 1, "female"));
        assertEquals("He sent you 5 messages.",   reread.format("messages", 5, "male"));
        assertEquals("She sent you 12 messages.", reread.format("messages", 12, "female"));
        assertEquals("They sent you 7 messages.", reread.format("messages", 7, "unknown"));
    }

    @Test
    void roundTrip_selectMode_nonOtherDefault() {
        // Fluent allows any variant to be the default; convention on write is *[other].
        String ftl = "salute = { $rank ->\n" +
                "   *[civilian] Hello\n" +
                "    [officer]  Salute\n" +
                "}\n";
        Lalein original = FluentLalein.fromString(ftl);
        assertEquals("Salute", original.format("salute", "officer"));
        // Unmatched keys fall back via the default — Parameter.other now mirrors the *-marked variant.
        assertEquals("Hello",  original.format("salute", "civilian"));
        assertEquals("Hello",  original.format("salute", "unknown"));
        // After a round-trip the writer emits *[other] (always); behaviour stays the same.
        Lalein reread = FluentLalein.fromString(FluentLalein.toString(original));
        assertEquals("Salute", reread.format("salute", "officer"));
        assertEquals("Hello",  reread.format("salute", "civilian"));
        assertEquals("Hello",  reread.format("salute", "unknown"));
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
