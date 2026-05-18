package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Select-mode: a Parameter with non-CLDR keys (e.g. "female", "male", "formal")
 * is selected by passing a String/Enum argument instead of a Number.
 * The "r" key remains the universal fallback when no custom key matches.
 */
class SelectModeTest {

    private static Lalein build(String handler, Map<String, Object> form) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(handler, form);
        return MapLalein.fromMap(data);
    }

    private static Map<String, Object> form(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
        return m;
    }

    // ── Basic select ────────────────────────────────────────────────────────

    @Test
    void simpleGender_directKeyLookup() {
        Lalein l = build("liked_post", form(
                "female", "She liked your post",
                "male",   "He liked your post",
                "r",      "They liked your post"));
        assertEquals("She liked your post",   l.format("liked_post", "female"));
        assertEquals("He liked your post",    l.format("liked_post", "male"));
        assertEquals("They liked your post",  l.format("liked_post", "other"));
        assertEquals("They liked your post",  l.format("liked_post", "non-binary"));
    }

    @Test
    void selectFallback_toR_whenKeyNotFound() {
        Lalein l = build("greeting", form(
                "formal", "Καλημέρα σας",
                "casual", "Γεια!",
                "r",      "Γεια σας"));
        assertEquals("Καλημέρα σας", l.format("greeting", "formal"));
        assertEquals("Γεια!",        l.format("greeting", "casual"));
        assertEquals("Γεια σας",     l.format("greeting", "unknown"));
    }

    @Test
    void nullStringArg_fallsToR() {
        Lalein l = build("verb", form(
                "female", "She",
                "r",      "They"));
        assertEquals("They", l.format("verb", (Object) null));
    }

    @Test
    void enumArg_resolvedByName() {
        Lalein l = build("verb", form(
                "FEMALE", "She",
                "MALE",   "He",
                "r",      "They"));
        // Enum's toString() returns name() by default
        assertEquals("She", l.format("verb", Gender.FEMALE));
        assertEquals("He",  l.format("verb", Gender.MALE));
        assertEquals("They", l.format("verb", Gender.NEUTRAL));
    }

    enum Gender { FEMALE, MALE, NEUTRAL }

    // ── Select alongside r without other CLDR keys ──────────────────────────

    @Test
    void selectWithOnlyR_isStillSelectMode() {
        // A parameter with custom keys plus "r" is select-mode.
        Lalein l = build("v", form("a", "Alpha", "b", "Beta", "r", "Other"));
        assertEquals("Alpha", l.format("v", "a"));
        assertEquals("Other", l.format("v", "xyz"));
    }

    @Test
    void emptyR_yieldsEmptyStringOnFallback() {
        Lalein l = build("v", form(
                "yes", "Yes",
                "no",  "No"));
        // No "r" key → "r" defaults to "" (Parameter constructor)
        assertEquals("Yes", l.format("v", "yes"));
        assertEquals("",    l.format("v", "maybe"));
    }

    // ── Pure plural-mode parameter rejects non-Number ───────────────────────

    @Test
    void pluralOnlyParameter_rejectsStringArg() {
        // Only CLDR keys → plural mode → expects Number, throws on String.
        Lalein l = build("apples", form(
                "o", "one apple",
                "r", "%d apples"));
        LaleinException ex = assertThrows(LaleinException.class,
                () -> l.format("apples", "not a number"));
        assertTrue(ex.getMessage().contains("numeric"));
    }

    @Test
    void pluralOnlyParameter_withCustomKey_acceptsString() {
        // Adding even one custom key flips the parameter into select mode.
        Lalein l = build("apples", form(
                "o",     "one apple",
                "r",     "%d apples",
                "many_more", "lots"));
        // String arg now permitted; "many_more" matches the custom key.
        assertEquals("lots", l.format("apples", "many_more"));
        // Unknown String arg falls to r — but r contains %d, so String.format errors.
        // Skipping that case here; it's a translator authoring error.
        assertEquals("one apple", l.format("apples", 1));
        assertEquals("5 apples",  l.format("apples", 5));
    }

    // ── Combined select + plural in complex form (the real-world case) ──────

    @Test
    void crossProduct_genderTimesPlural_nestedReference() {
        // Each cell of the (gender × count) table is written verbatim — no factorization,
        // mirroring how ICU nested {gender, select, ...} works.
        Map<String, Object> verb = form(
                "female", "%{female_count}",
                "male",   "%{male_count}",
                "r",      "%{other_count}");
        Map<String, Object> femaleCount = form("i", 2,
                "z", "She doesn't have apples",
                "o", "She has 1 apple",
                "r", "She has %2$d apples");
        Map<String, Object> maleCount = form("i", 2,
                "z", "He doesn't have apples",
                "o", "He has 1 apple",
                "r", "He has %2$d apples");
        Map<String, Object> otherCount = form("i", 2,
                "z", "They don't have apples",
                "o", "They have 1 apple",
                "r", "They have %2$d apples");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "%{verb}");
        msg.put("verb", verb);
        msg.put("female_count", femaleCount);
        msg.put("male_count",   maleCount);
        msg.put("other_count",  otherCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_apples", msg);
        Lalein l = MapLalein.fromMap(data);

        assertEquals("She doesn't have apples", l.format("user_apples", "female", 0));
        assertEquals("She has 1 apple",         l.format("user_apples", "female", 1));
        assertEquals("She has 5 apples",        l.format("user_apples", "female", 5));
        assertEquals("He doesn't have apples",  l.format("user_apples", "male",   0));
        assertEquals("He has 1 apple",          l.format("user_apples", "male",   1));
        assertEquals("They don't have apples",  l.format("user_apples", "other",  0));
        assertEquals("They have 3 apples",      l.format("user_apples", "other",  3));
    }

    // ── Round-trip preservation ─────────────────────────────────────────────

    @Test
    void roundTrip_selectMode_preserved() {
        Lalein first = build("v", form(
                "female", "She", "male", "He", "r", "They"));
        Map<String, Object> dumped = MapLalein.toMap(first);
        Lalein second = MapLalein.fromMap(dumped);
        assertEquals(first.format("v", "female"), second.format("v", "female"));
        assertEquals(first.format("v", "male"),   second.format("v", "male"));
        assertEquals(first.format("v", "other"),  second.format("v", "other"));
    }

    @Test
    void roundTrip_mixedCldrAndCustom_preserved() {
        Lalein first = build("greeting", form(
                "formal", "Hello, sir",
                "casual", "Hey",
                "o",      "Hi (alone)",     // CLDR plural alongside select
                "r",      "Hi"));
        Map<String, Object> dumped = MapLalein.toMap(first);
        Lalein second = MapLalein.fromMap(dumped);
        assertEquals("Hello, sir", second.format("greeting", "formal"));
        assertEquals("Hey",        second.format("greeting", "casual"));
        assertEquals("Hi",         second.format("greeting", "anything-else"));
        assertEquals("Hi (alone)", second.format("greeting", 1));
        assertEquals("Hi",         second.format("greeting", 5));
    }

    // ── Reserved keys: format and i ─────────────────────────────────────────

    @Test
    void formatKeyInsideParameter_throws() {
        // "format" remains reserved; it cannot appear as a form name inside a parameter.
        Map<String, Object> verb = new LinkedHashMap<>();
        verb.put("female", "She");
        verb.put("format", "boom");
        verb.put("r",      "They");
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("verb", verb);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("k", msg);
        LaleinException ex = assertThrows(LaleinException.class, () -> MapLalein.fromMap(data));
        assertTrue(ex.getMessage().contains("format"));
    }

    @Test
    void iKeyIsAlwaysTheArgumentIndex_evenWhenCustomKeysExist() {
        // i still controls argumentIndex even in select mode.
        Lalein l = build("v", form(
                "i", 2,
                "female", "She",
                "male",   "He",
                "r",      "They"));
        // arg #1 ignored; arg #2 drives the select
        assertEquals("She",  l.format("v", 99, "female"));
        assertEquals("He",   l.format("v", 99, "male"));
        assertEquals("They", l.format("v", 99, "unknown"));
    }
}
