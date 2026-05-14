package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class XcStringsLaleinTest {

    private static Lalein loadCanonical() {
        return XcStringsLalein.fromResource("/Localizable.xcstrings", "en");
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
    void readsMultiParameterPlurals_basketsWithOranges() {
        Lalein lalein = loadCanonical();
        assertEquals("I don't have a basket or an orange.",        lalein.format("baskets_with_oranges", 0, 0));
        assertEquals("I don't have a basket but I have an orange.", lalein.format("baskets_with_oranges", 0, 1));
        assertEquals("I don't have a basket but I have 10 oranges.", lalein.format("baskets_with_oranges", 0, 10));
        assertEquals("I have a basket without oranges.",            lalein.format("baskets_with_oranges", 1, 0));
        assertEquals("I have a basket with one orange.",            lalein.format("baskets_with_oranges", 1, 1));
        assertEquals("I have a basket with 8 oranges.",             lalein.format("baskets_with_oranges", 1, 8));
        assertEquals("I have 7 baskets without oranges.",           lalein.format("baskets_with_oranges", 7, 0));
        assertEquals("I have 7 baskets with one orange.",           lalein.format("baskets_with_oranges", 7, 1));
        assertEquals("I have 7 baskets with 9 oranges.",            lalein.format("baskets_with_oranges", 7, 9));
    }

    @Test
    void missingHandler_returnedAsIs() {
        Lalein lalein = loadCanonical();
        assertEquals("This does not exist", lalein.format("This does not exist"));
    }

    @Test
    void nullLanguage_fallsBackToSourceLanguage() {
        Lalein lalein = XcStringsLalein.fromResource("/Localizable.xcstrings", null);
        assertEquals("I have peaches.", lalein.format("peaches"));
        assertEquals("I have an apple.", lalein.format("apples", 1));
    }

    @Test
    void missingLocale_entriesOmittedAndHandlerReturnedAsIs() {
        // Only "en" exists in the catalog; loading "fr" finds nothing for any entry.
        Lalein lalein = XcStringsLalein.fromResource("/Localizable.xcstrings", "fr");
        assertEquals("peaches", lalein.format("peaches"));
        assertEquals("apples", lalein.format("apples", 1));
    }

    @Test
    void fromString_simple() {
        String json = "{ \"sourceLanguage\":\"en\", \"strings\":{" +
                "\"hi\":{\"localizations\":{\"en\":{\"stringUnit\":{\"state\":\"translated\",\"value\":\"Hello\"}}}}" +
                "}}";
        Lalein lalein = XcStringsLalein.fromString(json, null);
        assertEquals("Hello", lalein.format("hi"));
    }

    @Test
    void fromString_unicode() {
        String json = "{ \"sourceLanguage\":\"el\", \"strings\":{" +
                "\"hi\":{\"localizations\":{\"el\":{\"stringUnit\":{\"value\":\"Χαίρετε\"}}}}" +
                "}}";
        Lalein lalein = XcStringsLalein.fromString(json, null);
        assertEquals("Χαίρετε", lalein.format("hi"));
    }

    @Test
    void fromStream_simple() {
        String json = "{ \"sourceLanguage\":\"en\", \"strings\":{" +
                "\"hi\":{\"localizations\":{\"en\":{\"stringUnit\":{\"value\":\"Hello\"}}}}" +
                "}}";
        Lalein lalein = XcStringsLalein.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), null);
        assertEquals("Hello", lalein.format("hi"));
    }

    @Test
    void fromReader_simple() {
        String json = "{\"sourceLanguage\":\"en\",\"strings\":{" +
                "\"k\":{\"localizations\":{\"en\":{\"stringUnit\":{\"value\":\"v\"}}}}}}";
        Lalein lalein = XcStringsLalein.fromReader(new StringReader(json), null);
        assertEquals("v", lalein.format("k"));
    }

    @Test
    void fromFile_missing_throws() {
        File ghost = new File("/tmp/never-exists-" + System.nanoTime() + ".xcstrings");
        assertThrows(LaleinException.class, () -> XcStringsLalein.fromFile(ghost, null));
    }

    @Test
    void fromJson_directBuild() {
        JsonObject root = Json.parse("{\"sourceLanguage\":\"en\",\"strings\":{" +
                "\"k\":{\"localizations\":{\"en\":{\"stringUnit\":{\"value\":\"v\"}}}}}}").asObject();
        Lalein lalein = XcStringsLalein.fromJson(root, null);
        assertEquals("v", lalein.format("k"));
    }

    @Test
    void noSourceLanguage_andNoExplicit_throws() {
        String json = "{\"strings\":{}}";
        assertThrows(LaleinException.class, () -> XcStringsLalein.fromString(json, null));
    }

    // === Write direction ===

    @Test
    void toJson_simpleTranslation() {
        Lalein lalein = XcStringsLalein.fromString(
                "{\"sourceLanguage\":\"en\",\"strings\":{\"k\":{\"localizations\":{\"en\":{\"stringUnit\":{\"value\":\"v\"}}}}}}",
                null);
        JsonObject out = XcStringsLalein.toJson(lalein, "en");
        assertEquals("en", out.getString("sourceLanguage", ""));
        JsonObject strings = out.get("strings").asObject();
        JsonObject k = strings.get("k").asObject();
        JsonObject loc = k.get("localizations").asObject().get("en").asObject();
        assertEquals("v", loc.get("stringUnit").asObject().getString("value", ""));
    }

    @Test
    void roundTrip_apples_preservesBehaviour() {
        Lalein original = loadCanonical();
        JsonObject json = XcStringsLalein.toJson(original, "en");
        Lalein reread = XcStringsLalein.fromJson(json, "en");
        assertEquals("I don't have apples.", reread.format("apples", 0));
        assertEquals("I have an apple.",    reread.format("apples", 1));
        assertEquals("I have two apples.",  reread.format("apples", 2));
        assertEquals("I have 42 apples.",   reread.format("apples", 42));
    }

    @Test
    void roundTrip_basketsWithOranges_preservesBehaviour() {
        Lalein original = loadCanonical();
        JsonObject json = XcStringsLalein.toJson(original, "en");
        Lalein reread = XcStringsLalein.fromJson(json, "en");
        assertEquals("I don't have a basket or an orange.", reread.format("baskets_with_oranges", 0, 0));
        assertEquals("I have a basket with one orange.",    reread.format("baskets_with_oranges", 1, 1));
        assertEquals("I have 3 baskets with 5 oranges.",    reread.format("baskets_with_oranges", 3, 5));
        // Stability across two cycles
        JsonObject again = XcStringsLalein.toJson(reread, "en");
        Lalein third = XcStringsLalein.fromJson(again, "en");
        assertEquals("I have 3 baskets with 5 oranges.", third.format("baskets_with_oranges", 3, 5));
    }

    @Test
    void roundTrip_simpleTranslation_compactOutput() {
        Lalein original = loadCanonical();
        JsonObject json = XcStringsLalein.toJson(original, "en");
        // peaches should be written as a plain stringUnit, no variations / substitutions
        JsonObject peaches = json.get("strings").asObject().get("peaches").asObject();
        JsonObject loc = peaches.get("localizations").asObject().get("en").asObject();
        assertTrue(loc.get("stringUnit") != null);
        assertNull(loc.get("variations"));
        assertNull(loc.get("substitutions"));
    }

    @Test
    void toJsonString_producesParseableJson() {
        Lalein lalein = loadCanonical();
        String text = XcStringsLalein.toJsonString(lalein, "en");
        // Re-parse and verify the structure works
        JsonObject reparsed = Json.parse(text).asObject();
        assertEquals("en", reparsed.getString("sourceLanguage", ""));
        Lalein restored = XcStringsLalein.fromJson(reparsed, "en");
        assertEquals("I have peaches.", restored.format("peaches"));
    }

    @Test
    void writeOnlyOneLocale() {
        // Verify the writer emits only the requested locale
        Lalein lalein = loadCanonical();
        JsonObject out = XcStringsLalein.toJson(lalein, "fr");
        JsonObject strings = out.get("strings").asObject();
        JsonObject apples = strings.get("apples").asObject();
        JsonObject localizations = apples.get("localizations").asObject();
        assertNotNull(localizations.get("fr"));
        assertNull(localizations.get("en"));
        assertEquals("fr", out.getString("sourceLanguage", ""));
    }

    @Test
    void appleFormatSpecifiersAreNormalised() {
        // Verify that %lld in xcstrings becomes %d in Lalein output
        String json = "{\"sourceLanguage\":\"en\",\"strings\":{" +
                "\"counter\":{\"localizations\":{\"en\":{\"variations\":{\"plural\":{" +
                "\"one\":{\"stringUnit\":{\"value\":\"%lld item\"}}," +
                "\"other\":{\"stringUnit\":{\"value\":\"%lld items\"}}" +
                "}}}}}}}";
        Lalein lalein = XcStringsLalein.fromString(json, null);
        assertEquals("1 item",  lalein.format("counter", 1));
        assertEquals("5 items", lalein.format("counter", 5));
    }

    @Test
    void argPlaceholderIsResolvedToPositionalSpec() {
        // %arg with formatSpecifier="lld" and argNum=2 should resolve to %2$d in Lalein
        String json = "{\"sourceLanguage\":\"en\",\"strings\":{" +
                "\"msg\":{\"localizations\":{\"en\":{" +
                "\"stringUnit\":{\"value\":\"a=%#@a@, b=%#@b@\"}," +
                "\"substitutions\":{" +
                "\"a\":{\"argNum\":1,\"formatSpecifier\":\"lld\",\"variations\":{\"plural\":{" +
                "\"one\":{\"stringUnit\":{\"value\":\"one\"}}," +
                "\"other\":{\"stringUnit\":{\"value\":\"%arg\"}}" +
                "}}}," +
                "\"b\":{\"argNum\":2,\"formatSpecifier\":\"lld\",\"variations\":{\"plural\":{" +
                "\"one\":{\"stringUnit\":{\"value\":\"one\"}}," +
                "\"other\":{\"stringUnit\":{\"value\":\"%arg\"}}" +
                "}}}" +
                "}}}}}}";
        Lalein lalein = XcStringsLalein.fromString(json, null);
        assertEquals("a=one, b=one",  lalein.format("msg", 1, 1));
        assertEquals("a=5, b=one",    lalein.format("msg", 5, 1));
        assertEquals("a=5, b=10",     lalein.format("msg", 5, 10));
    }
}
