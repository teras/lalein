package com.panayotis.lalein;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursive-descent parser for the Fluent subset we support.
 * Builds a {@link Lalein} from the source text.
 */
class FluentParser {

    private final String src;
    private int pos;

    /** Tracks the order in which each Fluent variable was first encountered. */
    private final Map<String, Integer> varIndices = new LinkedHashMap<>();
    /** Accumulated Parameters across all nested selectors of the current entry. */
    private Map<String, Parameter> currentParams;

    FluentParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    Lalein parse() {
        Map<String, Translation> translations = new LinkedHashMap<>();
        while (true) {
            skipWhitespaceAndComments();
            if (eof()) break;
            String name = parseIdentifier();
            skipInlineSpace();
            expect('=');
            skipInlineSpace();
            translations.put(name, parseEntryValue());
        }
        return new Lalein(translations);
    }

    private Translation parseEntryValue() {
        // Reset per-entry state — each message has its own variable scope.
        varIndices.clear();
        currentParams = new LinkedHashMap<>();

        if (peekNonInlineSpace() == '{') {
            skipInlineSpace();
            // The value is a placeable expression at top level. For our schema this is
            // a select expression that drives the master parameter.
            Object placeable = parsePlaceable();
            return convertTopLevel(placeable);
        }
        // Simple text value: read the rest of the line.
        String value = readToEndOfLine();
        return new Translation(value, null);
    }

    private Translation convertTopLevel(Object placeable) {
        if (placeable instanceof VarRef) {
            VarRef vr = (VarRef) placeable;
            int idx = getOrAssignIndex(vr.var);
            return new Translation("%" + idx + "$d", null);
        }
        SelectExpr se = (SelectExpr) placeable;
        int masterIdx = getOrAssignIndex(se.var);
        Parameter masterParam = buildParameter(se, masterIdx);
        currentParams.put(se.var, masterParam);
        // Reorder parameters so the master is first to satisfy Lalein's first-key inference
        // even though the explicit format reference would also do it.
        Map<String, Parameter> ordered = new LinkedHashMap<>();
        ordered.put(se.var, currentParams.remove(se.var));
        ordered.putAll(currentParams);
        return new Translation("%{" + se.var + "}", ordered);
    }

    private Parameter buildParameter(SelectExpr se, int ownIdx) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (Variant v : se.variants)
            forms.put(v.key, patternToText(v.pattern, se.var, ownIdx));
        return new Parameter(ownIdx,
                forms.get("zero"),
                forms.get("one"),
                forms.get("two"),
                forms.get("few"),
                forms.get("many"),
                forms.get("other"));
    }

    private String patternToText(List<Object> pattern, String ownVar, int ownIdx) {
        StringBuilder out = new StringBuilder();
        for (Object el : pattern) {
            if (el instanceof String) {
                out.append((String) el);
            } else if (el instanceof VarRef) {
                String var = ((VarRef) el).var;
                if (var.equals(ownVar)) {
                    // Referencing the selector's own value — interpolate as integer.
                    out.append('%').append(ownIdx).append("$d");
                } else {
                    int idx = getOrAssignIndex(var);
                    out.append("%{").append(var).append("}");
                }
            } else if (el instanceof SelectExpr) {
                SelectExpr nested = (SelectExpr) el;
                int nestedIdx = getOrAssignIndex(nested.var);
                if (!currentParams.containsKey(nested.var))
                    currentParams.put(nested.var, buildParameter(nested, nestedIdx));
                out.append("%{").append(nested.var).append("}");
            }
        }
        return out.toString();
    }

    private int getOrAssignIndex(String var) {
        Integer existing = varIndices.get(var);
        if (existing != null) return existing;
        int idx = varIndices.size() + 1;
        varIndices.put(var, idx);
        return idx;
    }

    // ===== Recursive descent =====

    /** Parses a {...} placeable. Returns either a VarRef or a SelectExpr. */
    private Object parsePlaceable() {
        expect('{');
        skipWs();
        expect('$');
        String var = parseIdentifier();
        skipWs();
        if (peek() == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '>') {
            // Select expression
            pos += 2;
            skipWs();
            List<Variant> variants = new ArrayList<>();
            while (peek() != '}') {
                variants.add(parseVariant());
                skipWs();
            }
            expect('}');
            if (variants.isEmpty())
                throw new LaleinException("Fluent select expression has no variants near pos " + pos);
            return new SelectExpr(var, variants);
        }
        expect('}');
        return new VarRef(var);
    }

    private Variant parseVariant() {
        boolean isDefault = false;
        if (peek() == '*') { pos++; isDefault = true; }
        expect('[');
        String key = parseIdentifier();
        expect(']');
        List<Object> pattern = parseVariantPattern();
        return new Variant(key, isDefault, pattern);
    }

    private List<Object> parseVariantPattern() {
        List<Object> elements = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        // Strip leading inline whitespace from the value of the variant
        while (peek() == ' ' || peek() == '\t') pos++;
        while (true) {
            if (eof()) break;
            char c = peek();
            if (c == '{') {
                flushText(elements, text);
                elements.add(parsePlaceable());
                continue;
            }
            if (c == '}') break; // parent's closing brace
            if (c == '\n') {
                // Look ahead: if next non-whitespace is '[' or '*[' or '}', this variant ends.
                int p = pos + 1;
                while (p < src.length() && (src.charAt(p) == ' ' || src.charAt(p) == '\t')) p++;
                if (p < src.length()) {
                    char la = src.charAt(p);
                    if (la == '[' || la == '}' || (la == '*' && p + 1 < src.length() && src.charAt(p + 1) == '[')) {
                        // Variant ended; do not consume the newline so the parent loop sees it.
                        break;
                    }
                }
                text.append(c);
                pos++;
                continue;
            }
            text.append(c);
            pos++;
        }
        flushText(elements, text);
        return trimTrailingTextWhitespace(elements);
    }

    private static List<Object> trimTrailingTextWhitespace(List<Object> elements) {
        if (elements.isEmpty()) return elements;
        Object last = elements.get(elements.size() - 1);
        if (last instanceof String) {
            String s = (String) last;
            int end = s.length();
            while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t' || s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r'))
                end--;
            if (end == 0) elements.remove(elements.size() - 1);
            else if (end < s.length()) elements.set(elements.size() - 1, s.substring(0, end));
        }
        return elements;
    }

    private static void flushText(List<Object> elements, StringBuilder text) {
        if (text.length() > 0) {
            elements.add(text.toString());
            text.setLength(0);
        }
    }

    // ===== Lexer-ish helpers =====

    private boolean eof() { return pos >= src.length(); }
    private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }

    private char peekNonInlineSpace() {
        int p = pos;
        while (p < src.length() && (src.charAt(p) == ' ' || src.charAt(p) == '\t')) p++;
        return p < src.length() ? src.charAt(p) : '\0';
    }

    private void skipInlineSpace() {
        while (!eof() && (peek() == ' ' || peek() == '\t')) pos++;
    }

    private void skipWs() {
        while (!eof() && Character.isWhitespace(peek())) pos++;
    }

    private void skipWhitespaceAndComments() {
        while (!eof()) {
            char c = peek();
            if (Character.isWhitespace(c)) { pos++; continue; }
            if (c == '#') { while (!eof() && peek() != '\n') pos++; continue; }
            break;
        }
    }

    private void expect(char c) {
        if (eof() || peek() != c)
            throw new LaleinException("Fluent parser expected '" + c + "' at position " + pos
                    + " but found " + (eof() ? "EOF" : "'" + peek() + "'"));
        pos++;
    }

    private String parseIdentifier() {
        int start = pos;
        while (!eof()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') pos++;
            else break;
        }
        if (start == pos)
            throw new LaleinException("Fluent identifier expected at position " + pos);
        return src.substring(start, pos);
    }

    private String readToEndOfLine() {
        int start = pos;
        while (!eof() && peek() != '\n') pos++;
        return src.substring(start, pos);
    }

    // ===== AST node types =====

    private static final class VarRef {
        final String var;
        VarRef(String var) { this.var = var; }
    }

    private static final class SelectExpr {
        final String var;
        final List<Variant> variants;
        SelectExpr(String var, List<Variant> variants) { this.var = var; this.variants = variants; }
    }

    private static final class Variant {
        final String key;
        final boolean isDefault;
        final List<Object> pattern;
        Variant(String key, boolean isDefault, List<Object> pattern) {
            this.key = key;
            this.isDefault = isDefault;
            this.pattern = pattern;
        }
    }
}
