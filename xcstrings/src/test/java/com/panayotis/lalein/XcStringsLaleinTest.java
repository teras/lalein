package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

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

    // === Select-mode (lossy) round-trip ===
    //
    // The xcstrings format only supports CLDR plural categories and device variations;
    // it has no native generic select. Per the backend's policy, custom (non-CLDR) keys
    // are dropped silently on write, leaving only the "other" fallback so the entry
    // still resolves at runtime.

    @Test
    void roundTrip_selectMode_dropsCustomKeysSilently() {
        // Build a Lalein with a select-mode parameter (gender) directly so we can exercise
        // the lossy write path without going through another backend.
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("female", "She liked your post");
        custom.put("male",   "He liked your post");
        Parameter gender = new Parameter(1, null, null, null, null, null,
                "They liked your post", custom);
        Map<String, Parameter> params = new LinkedHashMap<>();
        params.put("gender", gender);
        Map<String, Translation> translations = new LinkedHashMap<>();
        translations.put("liked_post", new Translation("%{gender}", params));
        Lalein original = new Lalein(translations);

        // Sanity check on the original behaviour before writing.
        assertEquals("She liked your post",   original.format("liked_post", "female"));
        assertEquals("He liked your post",    original.format("liked_post", "male"));
        assertEquals("They liked your post",  original.format("liked_post", "unknown"));

        JsonObject json = XcStringsLalein.toJson(original, "en");
        Lalein reread = XcStringsLalein.fromJson(json, "en");

        // After the lossy round-trip, custom keys are gone — only the "other" fallback
        // remains. The Parameter is now numeric-mode, so callers must pass a Number.
        // (Passing a String would throw, since custom is null.)
        assertEquals("They liked your post",  reread.format("liked_post", 0));
        assertEquals("They liked your post",  reread.format("liked_post", 1));
        assertEquals("They liked your post",  reread.format("liked_post", 42));
        assertThrows(LaleinException.class, () -> reread.format("liked_post", "female"));
    }

    @Test
    void selectMode_writtenAsPluralOtherOnly() {
        // Verify the emitted JSON has no traces of the custom keys — only an "other" branch
        // remains, demonstrating that the lossy conversion is silent and predictable.
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("formal", "Καλημέρα σας");
        custom.put("casual", "Γεια!");
        Parameter register = new Parameter(1, null, null, null, null, null,
                "Γεια σας", custom);
        Map<String, Parameter> params = new LinkedHashMap<>();
        params.put("register", register);
        Map<String, Translation> translations = new LinkedHashMap<>();
        translations.put("greeting", new Translation("%{register}", params));
        Lalein original = new Lalein(translations);

        JsonObject json = XcStringsLalein.toJson(original, "el");
        JsonObject loc = json.get("strings").asObject()
                .get("greeting").asObject()
                .get("localizations").asObject()
                .get("el").asObject();
        JsonObject plural = loc.get("variations").asObject().get("plural").asObject();
        assertNotNull(plural.get("other"), "other branch present");
        assertNull(plural.get("formal"),   "custom keys must not leak into output");
        assertNull(plural.get("casual"),   "custom keys must not leak into output");
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
