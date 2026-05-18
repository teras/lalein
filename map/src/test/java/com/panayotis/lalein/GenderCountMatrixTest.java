package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Full cross-product matrix: 3 genders × 4 count values (0, 1, 2, N=many).
 * Every cell of the 12-entry grid is asserted explicitly. The data shape uses
 * a select-mode `verb` Parameter that nests into one plural-mode Parameter per
 * gender — the canonical pattern from the README's `user_apples` example.
 */
class GenderCountMatrixTest {

    private static Lalein build() {
        Map<String, Object> verb = new LinkedHashMap<>();
        verb.put("female", "%{female_count}");
        verb.put("male",   "%{male_count}");
        verb.put("r",      "%{other_count}");

        Map<String, Object> femaleCount = new LinkedHashMap<>();
        femaleCount.put("i", 2);
        femaleCount.put("z", "She doesn't have apples");
        femaleCount.put("o", "She has 1 apple");
        femaleCount.put("t", "She has 2 apples");
        femaleCount.put("r", "She has %2$d apples");

        Map<String, Object> maleCount = new LinkedHashMap<>();
        maleCount.put("z", "He doesn't have apples");
        maleCount.put("o", "He has 1 apple");
        maleCount.put("t", "He has 2 apples");
        maleCount.put("r", "He has %2$d apples");

        Map<String, Object> otherCount = new LinkedHashMap<>();
        otherCount.put("z", "They don't have apples");
        otherCount.put("o", "They have 1 apple");
        otherCount.put("t", "They have 2 apples");
        otherCount.put("r", "They have %2$d apples");

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("format", "%{verb}");
        msg.put("verb", verb);
        msg.put("female_count", femaleCount);
        msg.put("^male_count",  maleCount);
        msg.put("^other_count", otherCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_apples", msg);
        return MapLalein.fromMap(data);
    }

    // ── 3 × 4 matrix: gender ∈ {female, male, other} × count ∈ {0, 1, 2, N} ──

    // count = 0 ─────────────────────────────────────────────────────────────

    @Test
    void female_zero() {
        assertEquals("She doesn't have apples", build().format("user_apples", "female", 0));
    }

    @Test
    void male_zero() {
        assertEquals("He doesn't have apples", build().format("user_apples", "male", 0));
    }

    @Test
    void other_zero() {
        assertEquals("They don't have apples", build().format("user_apples", "other", 0));
    }

    // count = 1 ─────────────────────────────────────────────────────────────

    @Test
    void female_one() {
        assertEquals("She has 1 apple", build().format("user_apples", "female", 1));
    }

    @Test
    void male_one() {
        assertEquals("He has 1 apple", build().format("user_apples", "male", 1));
    }

    @Test
    void other_one() {
        assertEquals("They have 1 apple", build().format("user_apples", "other", 1));
    }

    // count = 2 ─────────────────────────────────────────────────────────────

    @Test
    void female_two() {
        assertEquals("She has 2 apples", build().format("user_apples", "female", 2));
    }

    @Test
    void male_two() {
        assertEquals("He has 2 apples", build().format("user_apples", "male", 2));
    }

    @Test
    void other_two() {
        assertEquals("They have 2 apples", build().format("user_apples", "other", 2));
    }

    // count = N (many) ──────────────────────────────────────────────────────

    @Test
    void female_many() {
        Lalein l = build();
        assertEquals("She has 5 apples",   l.format("user_apples", "female", 5));
        assertEquals("She has 100 apples", l.format("user_apples", "female", 100));
    }

    @Test
    void male_many() {
        Lalein l = build();
        assertEquals("He has 3 apples",   l.format("user_apples", "male", 3));
        assertEquals("He has 42 apples",  l.format("user_apples", "male", 42));
    }

    @Test
    void other_many() {
        Lalein l = build();
        assertEquals("They have 7 apples",   l.format("user_apples", "other", 7));
        assertEquals("They have 999 apples", l.format("user_apples", "other", 999));
    }

    // ── Unknown gender falls through to `r` (= other) ───────────────────────

    @Test
    void unknownGender_routesToOtherBranch() {
        Lalein l = build();
        assertEquals("They don't have apples", l.format("user_apples", "non-binary", 0));
        assertEquals("They have 1 apple",      l.format("user_apples", "non-binary", 1));
        assertEquals("They have 2 apples",     l.format("user_apples", "non-binary", 2));
        assertEquals("They have 4 apples",     l.format("user_apples", "non-binary", 4));
    }

    @Test
    void nullGender_routesToOtherBranch() {
        Lalein l = build();
        assertEquals("They don't have apples", l.format("user_apples", null, 0));
        assertEquals("They have 8 apples",     l.format("user_apples", null, 8));
    }

    // ── Boundary tolerances on the count axis ───────────────────────────────

    @Test
    void floatNearZero_naturalZeroBandApplies() {
        Lalein l = build();
        assertEquals("She doesn't have apples", l.format("user_apples", "female", 0.0));
        assertEquals("She doesn't have apples", l.format("user_apples", "female", -0.0000005));
    }

    @Test
    void floatNearOne_naturalOneBandApplies() {
        Lalein l = build();
        assertEquals("She has 1 apple", l.format("user_apples", "female", 1.0));
        assertEquals("She has 1 apple", l.format("user_apples", "female", 0.9999995));
    }

    @Test
    void floatNearTwo_naturalTwoBandApplies() {
        Lalein l = build();
        assertEquals("She has 2 apples", l.format("user_apples", "female", 2.0));
        assertEquals("She has 2 apples", l.format("user_apples", "female", 2.0000005));
    }

    // ── Various Number subtypes feed through correctly ──────────────────────

    @Test
    void variousNumberSubtypes() {
        Lalein l = build();
        // Each subtype must (a) drive plural selection correctly and
        // (b) format the %2$d placeholder in the "r" branch.
        assertEquals("She has 1 apple",  l.format("user_apples", "female", (byte) 1));
        assertEquals("She has 1 apple",  l.format("user_apples", "female", (short) 1));
        assertEquals("She has 1 apple",  l.format("user_apples", "female", 1L));
        assertEquals("She has 2 apples", l.format("user_apples", "female", java.math.BigInteger.valueOf(2)));
        assertEquals("He has 7 apples",  l.format("user_apples", "male",   7));
    }
}
