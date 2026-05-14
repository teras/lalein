package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backend for Apple's String Catalog format (.xcstrings) introduced with
 * Xcode 15. A String Catalog is a JSON document that consolidates all
 * translations and plural rules for a single source key across every locale.
 *
 * <p>Lalein represents a translation set for one locale at a time, so all read
 * methods accept a {@code language} parameter that selects which localization
 * to extract. Pass {@code null} to fall back to the catalog's
 * {@code sourceLanguage}. The write methods emit a single-locale catalog using
 * the supplied language code.
 */
@SuppressWarnings("unused")
public class XcStringsLalein {

    private static final String SOURCE_LANGUAGE = "sourceLanguage";
    private static final String VERSION         = "version";
    private static final String STRINGS         = "strings";
    private static final String EXTRACTION      = "extractionState";
    private static final String LOCALIZATIONS   = "localizations";
    private static final String STRING_UNIT     = "stringUnit";
    private static final String STATE           = "state";
    private static final String VALUE           = "value";
    private static final String VARIATIONS      = "variations";
    private static final String PLURAL          = "plural";
    private static final String SUBSTITUTIONS   = "substitutions";
    private static final String ARG_NUM         = "argNum";
    private static final String FORMAT_SPEC     = "formatSpecifier";

    /** Apple variable reference within a top-level format value:  %[N$]#@varname@ */
    private static final Pattern APPLE_VAR_REF = Pattern.compile("%(?:(\\d+)\\$)?#@(\\w+)@");
    /** The %arg placeholder used inside substitution variations. */
    private static final Pattern ARG_PLACEHOLDER = Pattern.compile("%arg\\b");
    /** Apple printf-style format spec we may need to translate to Java conventions. */
    private static final Pattern APPLE_FORMAT_SPEC = Pattern.compile(
            "%(\\d+\\$)?([-+ #0]*\\d*(?:\\.\\d+)?)(l{1,2}|q|h{1,2}|z|t|j)?([diuoxXfFeEgGaAcCsS@p])"
    );
    /** Lalein variable reference. */
    private static final Pattern LALEIN_VAR_REF = Pattern.compile("%\\{(\\w+)}");
    /** Java format spec — only %s differs from Apple (which uses %@). */
    private static final Pattern JAVA_FORMAT_SPEC = Pattern.compile(
            "%(\\d+\\$)?([-+ #0]*\\d*(?:\\.\\d+)?)([diuoxXfFeEgGaAcCsS@])"
    );

    // === Entry points (read) ===

    public static Lalein fromResource(String resource, String language) {
        try (InputStream is = XcStringsLalein.class.getResourceAsStream(resource)) {
            return fromStream(is, language);
        } catch (IOException e) {
            throw new LaleinException("XCStrings", "resource " + resource, e);
        }
    }

    public static Lalein fromString(String data, String language) {
        return fromJson(Json.parse(data).asObject(), language);
    }

    public static Lalein fromFile(File data, String language) {
        try (Reader reader = new FileReader(data)) {
            return fromJson(Json.parse(reader).asObject(), language);
        } catch (IOException e) {
            throw new LaleinException("XCStrings", "file " + data.getAbsolutePath(), e);
        }
    }

    public static Lalein fromReader(Reader data, String language) {
        try {
            return fromJson(Json.parse(data).asObject(), language);
        } catch (IOException e) {
            throw new LaleinException("XCStrings", data.getClass().getName(), e);
        }
    }

    public static Lalein fromStream(InputStream data, String language) {
        try {
            return fromJson(Json.parse(new InputStreamReader(data, StandardCharsets.UTF_8)).asObject(), language);
        } catch (IOException e) {
            throw new LaleinException("XCStrings", data.getClass().getName(), e);
        }
    }

    public static Lalein fromJson(JsonObject root, String language) {
        String resolvedLanguage = language != null ? language : root.getString(SOURCE_LANGUAGE, null);
        if (resolvedLanguage == null)
            throw new LaleinException("XCStrings catalog has no sourceLanguage and no language was specified");
        JsonValue stringsVal = root.get(STRINGS);
        if (stringsVal == null || !stringsVal.isObject())
            return new Lalein(new LinkedHashMap<>());
        Map<String, Translation> translations = new LinkedHashMap<>();
        for (JsonObject.Member entry : stringsVal.asObject()) {
            Translation t = readEntry(entry.getName(), entry.getValue(), resolvedLanguage);
            if (t != null) translations.put(entry.getName(), t);
        }
        return new Lalein(translations);
    }

    // === Entry points (write) ===

    public static JsonObject toJson(Lalein lalein, String language) {
        if (language == null) language = "en";
        JsonObject root = new JsonObject();
        root.add(SOURCE_LANGUAGE, language);
        root.add(VERSION, "1.0");
        JsonObject strings = new JsonObject();
        for (Map.Entry<String, Translation> e : lalein.entries())
            strings.add(e.getKey(), writeEntry(e.getValue(), language));
        root.add(STRINGS, strings);
        return root;
    }

    public static String toJsonString(Lalein lalein, String language) {
        return toJson(lalein, language).toString();
    }

    // === Read internals ===

    private static Translation readEntry(String handler, JsonValue entryVal, String language) {
        if (!entryVal.isObject()) return null;
        JsonObject entry = entryVal.asObject();
        JsonObject localizations = optObject(entry, LOCALIZATIONS);
        if (localizations == null) return null;
        JsonObject loc = optObject(localizations, language);
        if (loc == null) return null;

        JsonObject substitutions = optObject(loc, SUBSTITUTIONS);
        JsonObject variations    = optObject(loc, VARIATIONS);
        JsonObject stringUnit    = optObject(loc, STRING_UNIT);

        if (substitutions != null && stringUnit != null) {
            // Multi-parameter case: master template + per-variable substitutions
            String masterFormat = stringUnit.getString(VALUE, "");
            String laleinFormat = convertAppleToLalein(masterFormat);
            Map<String, Parameter> parameters = new LinkedHashMap<>();
            for (JsonObject.Member sm : substitutions) {
                String varName = sm.getName();
                if (!sm.getValue().isObject()) continue;
                JsonObject sub = sm.getValue().asObject();
                int argNum = sub.getInt(ARG_NUM, 1);
                String fmtSpec = sub.getString(FORMAT_SPEC, "lld");
                Parameter p = readSubstitutionParameter(sub, argNum, fmtSpec);
                if (p != null) parameters.put(varName, p);
            }
            return new Translation(laleinFormat, parameters.isEmpty() ? null : parameters);
        }
        if (variations != null) {
            // Single-parameter plural at the localization level
            JsonObject plural = optObject(variations, PLURAL);
            if (plural == null) return null;
            Parameter base = readPluralForms(plural, 1, null);
            if (base == null) return null;
            Map<String, Parameter> parameters = new LinkedHashMap<>();
            parameters.put("base", base);
            return new Translation("%{base}", parameters);
        }
        if (stringUnit != null) {
            // Simple string translation
            return new Translation(stringUnit.getString(VALUE, ""), null);
        }
        return null;
    }

    private static Parameter readSubstitutionParameter(JsonObject sub, int argNum, String fmtSpec) {
        JsonObject variations = optObject(sub, VARIATIONS);
        if (variations == null) return null;
        JsonObject plural = optObject(variations, PLURAL);
        if (plural == null) return null;
        return readPluralForms(plural, argNum, fmtSpec);
    }

    private static Parameter readPluralForms(JsonObject plural, int argNum, String fmtSpec) {
        String z = readPluralValue(plural, "zero",  argNum, fmtSpec);
        String o = readPluralValue(plural, "one",   argNum, fmtSpec);
        String t = readPluralValue(plural, "two",   argNum, fmtSpec);
        String f = readPluralValue(plural, "few",   argNum, fmtSpec);
        String m = readPluralValue(plural, "many",  argNum, fmtSpec);
        String r = readPluralValue(plural, "other", argNum, fmtSpec);
        return new Parameter(argNum, z, o, t, f, m, r);
    }

    private static String readPluralValue(JsonObject plural, String key, int argNum, String fmtSpec) {
        JsonObject form = optObject(plural, key);
        if (form == null) return null;
        JsonObject su = optObject(form, STRING_UNIT);
        if (su == null) return null;
        String raw = su.getString(VALUE, null);
        if (raw == null) return null;
        if (fmtSpec != null) {
            // Expand the %arg placeholder using this substitution's positional spec.
            String javaType = appleFormatToJavaConversion(fmtSpec);
            if (javaType != null)
                raw = ARG_PLACEHOLDER.matcher(raw).replaceAll(Matcher.quoteReplacement("%" + argNum + "$" + javaType));
        }
        return convertAppleToLalein(raw);
    }

    // === Write internals ===

    private static JsonObject writeEntry(Translation translation, String language) {
        JsonObject entry = new JsonObject();
        entry.add(EXTRACTION, "manual");
        JsonObject localizations = new JsonObject();
        JsonObject loc = new JsonObject();

        Map<String, Parameter> params = translation.parameters;
        if (params == null || params.isEmpty()) {
            loc.add(STRING_UNIT, makeStringUnit(translation.format));
        } else if (params.size() == 1 && params.values().iterator().next().argumentIndex == 1
                && ("%{" + params.keySet().iterator().next() + "}").equals(translation.format)) {
            // Compact single-parameter plural without substitutions
            Parameter p = params.values().iterator().next();
            JsonObject pluralObj = pluralFormsToJson(p, null, 1);
            JsonObject varObj = new JsonObject();
            varObj.add(PLURAL, pluralObj);
            loc.add(VARIATIONS, varObj);
        } else {
            // Multi-parameter case with substitutions
            loc.add(STRING_UNIT, makeStringUnit(convertLaleinToApple(translation.format, params)));
            JsonObject subs = new JsonObject();
            for (Map.Entry<String, Parameter> pe : params.entrySet()) {
                Parameter p = pe.getValue();
                JsonObject sub = new JsonObject();
                sub.add(ARG_NUM, p.argumentIndex);
                sub.add(FORMAT_SPEC, "lld");
                JsonObject varObj = new JsonObject();
                varObj.add(PLURAL, pluralFormsToJson(p, params, p.argumentIndex));
                sub.add(VARIATIONS, varObj);
                subs.add(pe.getKey(), sub);
            }
            loc.add(SUBSTITUTIONS, subs);
        }

        localizations.add(language, loc);
        entry.add(LOCALIZATIONS, localizations);
        return entry;
    }

    private static JsonObject pluralFormsToJson(Parameter p, Map<String, Parameter> allParams, int ownArgNum) {
        JsonObject out = new JsonObject();
        addPluralForm(out, "zero",  p.zero,  allParams, ownArgNum);
        addPluralForm(out, "one",   p.one,   allParams, ownArgNum);
        addPluralForm(out, "two",   p.two,   allParams, ownArgNum);
        addPluralForm(out, "few",   p.few,   allParams, ownArgNum);
        addPluralForm(out, "many",  p.many,  allParams, ownArgNum);
        if (p.other != null && !p.other.isEmpty())
            addPluralForm(out, "other", p.other, allParams, ownArgNum);
        return out;
    }

    private static void addPluralForm(JsonObject out, String key, String laleinValue,
                                       Map<String, Parameter> allParams, int ownArgNum) {
        if (laleinValue == null) return;
        String appleValue = convertLaleinToApple(laleinValue, allParams);
        // Replace the own positional %N$d with %arg for compactness (Apple convention)
        String marker = "%" + ownArgNum + "$d";
        appleValue = appleValue.replace(marker, "%arg");
        out.add(key, wrapInForm(appleValue));
    }

    private static JsonObject wrapInForm(String value) {
        JsonObject form = new JsonObject();
        form.add(STRING_UNIT, makeStringUnit(value));
        return form;
    }

    private static JsonObject makeStringUnit(String value) {
        JsonObject su = new JsonObject();
        su.add(STATE, "translated");
        su.add(VALUE, value);
        return su;
    }

    // === Format conversion helpers ===

    private static String convertAppleToLalein(String s) {
        if (s == null) return null;
        // Pass 1: Apple variable refs → Lalein refs
        Matcher m1 = APPLE_VAR_REF.matcher(s);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find())
            m1.appendReplacement(sb1, Matcher.quoteReplacement("%{" + m1.group(2) + "}"));
        m1.appendTail(sb1);
        // Pass 2: Apple printf specs → Java-compatible specs
        Matcher m2 = APPLE_FORMAT_SPEC.matcher(sb1.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String positional = m2.group(1) == null ? "" : m2.group(1);
            String flagsWidthPrec = m2.group(2) == null ? "" : m2.group(2);
            String javaConv = appleToJavaConversion(m2.group(4));
            if (javaConv == null) {
                m2.appendReplacement(sb2, Matcher.quoteReplacement(m2.group()));
                continue;
            }
            m2.appendReplacement(sb2, Matcher.quoteReplacement("%" + positional + flagsWidthPrec + javaConv));
        }
        m2.appendTail(sb2);
        return sb2.toString();
    }

    private static String convertLaleinToApple(String s, Map<String, Parameter> params) {
        if (s == null) return null;
        // Pass 1: %{var} → %N$#@var@ (with positional spec from the param's argumentIndex)
        Matcher m1 = LALEIN_VAR_REF.matcher(s);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            String varname = m1.group(1);
            Parameter p = params == null ? null : params.get(varname);
            int idx = p == null ? 1 : p.argumentIndex;
            m1.appendReplacement(sb1, Matcher.quoteReplacement("%" + idx + "$#@" + varname + "@"));
        }
        m1.appendTail(sb1);
        // Pass 2: Java %s → Apple %@
        Matcher m2 = JAVA_FORMAT_SPEC.matcher(sb1.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String positional = m2.group(1) == null ? "" : m2.group(1);
            String flagsWidthPrec = m2.group(2) == null ? "" : m2.group(2);
            String conv = m2.group(3);
            String appleConv = "s".equals(conv) ? "@" : conv;
            m2.appendReplacement(sb2, Matcher.quoteReplacement("%" + positional + flagsWidthPrec + appleConv));
        }
        m2.appendTail(sb2);
        return sb2.toString();
    }

    private static String appleToJavaConversion(String appleConv) {
        switch (appleConv) {
            case "d": case "i": case "u": return "d";
            case "o": return "o";
            case "x": return "x";
            case "X": return "X";
            case "f": case "F":
            case "e": case "E":
            case "g": case "G":
            case "a": case "A": return appleConv;
            case "c": case "C": return "c";
            case "s": case "S": return "s";
            case "@": return "s";
            default: return null;
        }
    }

    private static String appleFormatToJavaConversion(String fmtSpec) {
        // formatSpecifier from xcstrings substitution — strip length modifiers.
        if (fmtSpec == null || fmtSpec.isEmpty()) return "d";
        // Drop length modifier prefix
        String stripped = fmtSpec.replaceAll("^(l{1,2}|q|h{1,2}|z|t|j)", "");
        if (stripped.isEmpty()) return "d";
        return appleToJavaConversion(stripped);
    }

    private static JsonObject optObject(JsonObject parent, String key) {
        JsonValue v = parent.get(key);
        return v != null && v.isObject() ? v.asObject() : null;
    }
}
