package com.panayotis.lalein;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emits Fluent (.ftl) source text for a {@link Lalein}. */
class FluentWriter {

    private static final Pattern LALEIN_VAR_REF = Pattern.compile("%\\{(\\w+)}");
    /** Matches both numeric and string positional specs — used to convert
     *  references back to {@code $name} placeables when the index corresponds
     *  to a known selector parameter. */
    private static final Pattern JAVA_POS_SPEC  = Pattern.compile("%(\\d+)\\$[ds]|%[ds]");

    String write(Lalein lalein) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Translation> e : lalein.entries()) {
            if (!first) out.append('\n');
            first = false;
            writeEntry(out, e.getKey(), e.getValue());
        }
        return out.toString();
    }

    private void writeEntry(StringBuilder out, String name, Translation t) {
        out.append(name).append(" = ");
        Map<String, Parameter> params = t.parameters;
        if (params == null || params.isEmpty()) {
            out.append(t.format).append('\n');
            return;
        }
        String selectorVar = resolveSelectorVar(t.format, params);
        Parameter selector = params.get(selectorVar);
        if (selector == null) {
            // Fall back to writing the format raw — shouldn't happen for normal input.
            out.append(t.format).append('\n');
            return;
        }
        writeSelect(out, selectorVar, selector, params, 0);
        out.append('\n');
    }

    private String resolveSelectorVar(String format, Map<String, Parameter> params) {
        // Format is expected to be "%{var}" — extract the inner name.
        Matcher m = LALEIN_VAR_REF.matcher(format);
        if (m.find()) return m.group(1);
        // Fall back to the first parameter key.
        return params.keySet().iterator().next();
    }

    private void writeSelect(StringBuilder out, String var, Parameter p,
                             Map<String, Parameter> allParams, int indent) {
        boolean isSelect = p.custom != null;
        out.append("{ $").append(var).append(" ->\n");
        // Use numeric-literal selectors for the exact-match slots so that an external
        // Fluent runtime (which treats zero/one/two as CLDR plural categories, not as
        // exact values) reproduces Lalein's exact-match-first semantics.
        writeVariantIfPresent(out, "0",     p.zero,  p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "1",     p.one,   p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "2",     p.two,   p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "few",   p.few,   p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "many",  p.many,  p.argumentIndex, allParams, indent + 1, false);
        if (isSelect) {
            for (Map.Entry<String, String> ce : p.custom.entrySet())
                writeVariantIfPresent(out, ce.getKey(), ce.getValue(), p.argumentIndex, allParams, indent + 1, false);
            // Convention: always emit *[other] as the default for select-mode.
            appendIndent(out, indent + 1);
            out.append("*[other] ");
            String otherVal = emptyToNull(p.other);
            if (otherVal != null)
                appendVariantValue(out, otherVal, p.argumentIndex, allParams, indent + 1);
            out.append('\n');
        } else {
            writeVariantIfPresent(out, "other", emptyToNull(p.other), p.argumentIndex, allParams, indent + 1, true);
        }
        appendIndent(out, indent);
        out.append('}');
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private void writeVariantIfPresent(StringBuilder out, String key, String value,
                                       int selectorIdx, Map<String, Parameter> allParams,
                                       int indent, boolean isDefault) {
        if (value == null) return;
        appendIndent(out, indent);
        if (isDefault) out.append('*');
        out.append('[').append(key).append("] ");
        appendVariantValue(out, value, selectorIdx, allParams, indent);
        out.append('\n');
    }

    private void appendVariantValue(StringBuilder out, String laleinText,
                                    int selectorIdx,
                                    Map<String, Parameter> allParams, int indent) {
        // Convert %{nested} to nested select expressions; convert any positional
        // spec whose index matches an enclosing selector back to "{ $name }".
        String s = laleinText;

        StringBuilder afterSpecs = new StringBuilder();
        Matcher m = JAVA_POS_SPEC.matcher(s);
        while (m.find()) {
            String posStr = m.group(1);
            int idx = posStr != null ? Integer.parseInt(posStr) : selectorIdx;
            String varName = null;
            for (Map.Entry<String, Parameter> e : allParams.entrySet())
                if (e.getValue().argumentIndex == idx) { varName = e.getKey(); break; }
            String repl = varName != null ? "{ $" + varName + " }" : m.group();
            m.appendReplacement(afterSpecs, Matcher.quoteReplacement(repl));
        }
        m.appendTail(afterSpecs);
        s = afterSpecs.toString();

        // Step 2: replace %{nested} with the rendered nested select expression
        Matcher m2 = LALEIN_VAR_REF.matcher(s);
        int last = 0;
        while (m2.find()) {
            out.append(s, last, m2.start());
            String var = m2.group(1);
            Parameter nested = allParams.get(var);
            if (nested != null) {
                writeSelect(out, var, nested, allParams, indent);
            } else {
                out.append(m2.group());
            }
            last = m2.end();
        }
        out.append(s, last, s.length());
    }

    private static void appendIndent(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) out.append("    ");
    }
}
