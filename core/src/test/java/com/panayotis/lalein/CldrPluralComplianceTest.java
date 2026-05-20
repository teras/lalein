package com.panayotis.lalein;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exhaustive matrix test: every language registered in Lalein's PluralResolvers
 * is exercised against the official CLDR cardinal rules (loaded from
 * {@code cldr-plurals.json} in test resources) over a wide range of values.
 * Each mismatch is reported with language, input, expected category, and
 * actual category. Zero deviations are currently allowed.
 */
class CldrPluralComplianceTest {

    private static Map<String, LinkedHashMap<String, String>> cldr;

    @BeforeAll
    static void loadCldr() { cldr = loadPlurals(); }

    @Test
    void allRegisteredLanguagesMatchCldr() {
        List<String> mismatches = new ArrayList<>();
        for (String lang : LANGUAGES) {
            LinkedHashMap<String, String> rules = cldr.get(lang);
            if (rules == null) {
                mismatches.add(lang + ": not present in cldr-plurals.json");
                continue;
            }
            PluralResolver lalein = PluralResolvers.usingLanguage(lang);
            for (double v : TEST_VALUES) {
                PluralType expected = cldrCategoryFor(rules, v);
                PluralType actual = normalize(lalein.findType(v));
                if (actual != expected)
                    mismatches.add(String.format("%-4s n=%-12s expected=%-5s got=%-5s",
                            lang, formatValue(v), name(expected), name(actual)));
            }
        }
        if (!mismatches.isEmpty())
            fail("CLDR compliance mismatches (" + mismatches.size() + "):\n  "
                    + String.join("\n  ", mismatches));
    }

    @Test
    void evaluatorSanity_recognizesCanonicalRules() {
        // Quick self-check: the evaluator must agree with hand-computed CLDR
        // categories for a small set of well-known inputs.
        assertTrue(matches("ar", "pluralRule-count-zero", 0));
        assertTrue(matches("ar", "pluralRule-count-few", 5));
        assertTrue(matches("ar", "pluralRule-count-many", 15));
        assertTrue(matches("ru", "pluralRule-count-one", 21));
        assertTrue(matches("ru", "pluralRule-count-few", 22));
        assertTrue(matches("ru", "pluralRule-count-many", 11));
        assertTrue(matches("fr", "pluralRule-count-one", 0));
        assertTrue(matches("fr", "pluralRule-count-one", 1));
        assertTrue(matches("fr", "pluralRule-count-many", 1_000_000));
    }

    private static boolean matches(String lang, String category, double n) {
        Map<String, String> rules = cldr.get(lang);
        assertNotNull(rules, "no rules for " + lang);
        String rule = rules.get(category);
        assertNotNull(rule, "no " + category + " for " + lang);
        return CldrRuleEvaluator.evaluate(rule, new CldrRuleEvaluator.Operands(n));
    }

    private static PluralType cldrCategoryFor(Map<String, String> rules, double n) {
        CldrRuleEvaluator.Operands op = new CldrRuleEvaluator.Operands(n);
        for (Map.Entry<String, String> e : rules.entrySet()) {
            String cat = stripPrefix(e.getKey());
            if ("other".equals(cat)) continue;
            if (CldrRuleEvaluator.evaluate(e.getValue(), op))
                return categoryToType(cat);
        }
        return PluralType.OTHER;
    }

    private static String stripPrefix(String key) {
        int i = key.lastIndexOf('-');
        return i >= 0 ? key.substring(i + 1) : key;
    }

    private static PluralType categoryToType(String cat) {
        switch (cat) {
            case "zero":  return PluralType.ZERO;
            case "one":   return PluralType.ONE;
            case "two":   return PluralType.TWO;
            case "few":   return PluralType.FEW;
            case "many":  return PluralType.MANY;
            case "other": return PluralType.OTHER;
        }
        throw new IllegalArgumentException(cat);
    }

    private static PluralType normalize(PluralType t) { return t == null ? PluralType.OTHER : t; }

    private static String name(PluralType t) { return t == null ? "OTHER" : t.name(); }

    private static String formatValue(double v) {
        return v == (long) v ? Long.toString((long) v) : Double.toString(v);
    }

    private static Map<String, LinkedHashMap<String, String>> loadPlurals() {
        Map<String, LinkedHashMap<String, String>> out = new LinkedHashMap<>();
        // Match every "name": { line, then accept the block only if name is a
        // base language code (2-3 lowercase letters). Region variants like
        // "pt-PT" must reset currentLang to null so their rules don't bleed
        // into the base language's entry.
        Pattern blockStart = Pattern.compile("^\\s+\"([^\"]+)\":\\s*\\{\\s*$");
        Pattern baseLang = Pattern.compile("[a-z]{2,3}");
        Pattern ruleLine = Pattern.compile("^\\s+\"(pluralRule-count-[a-z]+)\":\\s*\"([^\"]*)\".*$");
        try (InputStream in = CldrPluralComplianceTest.class.getResourceAsStream("/cldr-plurals.json")) {
            assertNotNull(in, "cldr-plurals.json not found on classpath");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String currentLang = null;
                for (String line; (line = r.readLine()) != null; ) {
                    Matcher rm = ruleLine.matcher(line);
                    if (rm.matches() && currentLang != null) {
                        out.get(currentLang).put(rm.group(1), rm.group(2));
                        continue;
                    }
                    Matcher bm = blockStart.matcher(line);
                    if (bm.matches()) {
                        String name = bm.group(1);
                        if (baseLang.matcher(name).matches()) {
                            currentLang = name;
                            out.put(currentLang, new LinkedHashMap<>());
                        } else {
                            currentLang = null;
                        }
                    }
                }
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return out;
    }

    /** Every language code currently registered in PluralResolvers' TABLE. */
    private static final List<String> LANGUAGES = Arrays.asList(
            "ak", "bho", "guw", "ln", "mg", "nso", "pa", "ti", "wa",
            "am", "as", "bn", "gu", "hi", "kn", "pcm", "fa", "zu",
            "ff", "hy", "kab",
            "da", "fr", "es", "it", "ca", "pt",
            "ru", "uk", "be",
            "sr", "hr", "bs", "sh",
            "lag", "si",
            "pl", "cs", "sk", "lt", "lv", "prg",
            "sl", "dsb", "hsb",
            "ar", "ars", "he", "cy", "ga", "gd", "gv",
            "ro", "mo", "mt", "is", "mk",
            "fil", "ceb", "tl",
            "shi", "tzm", "kw", "br"
    );

    /** Inputs probed for every language. Covers small integers (densely),
     *  large round numbers, and a few canonical fractional values. */
    private static final double[] TEST_VALUES = buildValues();

    private static double[] buildValues() {
        List<Double> list = new ArrayList<>();
        for (int n = 0; n <= 200; n++) list.add((double) n);
        list.addAll(Arrays.asList(
                // Round large integers
                1000.0, 10000.0, 100000.0, 1_000_000.0, 1_000_001.0, 2_000_000.0,
                // 4-digit integers — probes m100 patterns at higher magnitudes
                // (kw FEW=1003, kw MANY=1001, ru ONE=1001/1021, lv ZERO=1011..1019)
                1001.0, 1002.0, 1003.0, 1004.0,
                1011.0, 1012.0, 1013.0, 1014.0, 1019.0,
                1021.0, 1022.0, 1024.0, 1101.0, 1121.0,
                // Cornish TWO via n%1000=0 and n%100000 ∈ {1000..20000,40000,60000,80000}
                5000.0, 15000.0, 20000.0, 40000.0, 60000.0, 80000.0,
                // Cornish TWO via n!=0 and n%1000000=100000
                1_100_000.0, 2_100_000.0,
                // Breton/French/Spanish MANY via n%1000000=0
                3_000_000.0,
                // Single-digit fractions (covers every f%10 ∈ {0..9}). Mirrors the
                // CLDR @decimal sample ranges 0.0~0.9, 1.1~1.9, 2.1~2.7.
                0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9,
                1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9,
                2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7,
                // Two-digit fractions (probes f%100 branches: 11..19 lv/prg ZERO,
                // 12..14 sr/hr exclusions, 3..4 sl/dsb/hsb FEW, exact 1/2 for dsb one/two).
                0.01, 0.02, 0.03, 0.04, 0.05, 0.11, 0.12, 0.13, 0.14, 0.15,
                0.21, 0.32, 0.91,
                1.01, 1.11, 1.21,
                // Larger decimals (probes i mod-100 with non-zero v).
                10.1, 10.2, 100.1,
                // Very small fractions — sinhala "i=0 and f=1" must catch 0.001, 0.0001
                // even though Double.toString switches to scientific notation at 1e-4.
                0.001, 0.0001,
                // Negative inputs — CLDR defines n = abs(source); Lalein must agree.
                -1.0, -5.0, -1.5, -21.0
        ));
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }
}
