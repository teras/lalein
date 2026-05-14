package com.panayotis.lalein;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emits Fluent (.ftl) source text for a {@link Lalein}. */
class FluentWriter {

    private static final Pattern LALEIN_VAR_REF = Pattern.compile("%\\{(\\w+)}");
    private static final Pattern JAVA_INT_SPEC  = Pattern.compile("%(\\d+)\\$d|%d");

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
        out.append("{ $").append(var).append(" ->\n");
        writeVariantIfPresent(out, "zero",  p.zero,  var, p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "one",   p.one,   var, p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "two",   p.two,   var, p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "few",   p.few,   var, p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "many",  p.many,  var, p.argumentIndex, allParams, indent + 1, false);
        writeVariantIfPresent(out, "other", emptyToNull(p.other), var, p.argumentIndex, allParams, indent + 1, true);
        appendIndent(out, indent);
        out.append('}');
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private void writeVariantIfPresent(StringBuilder out, String key, String value,
                                       String selectorVar, int selectorIdx,
                                       Map<String, Parameter> allParams,
                                       int indent, boolean isDefault) {
        if (value == null) return;
        appendIndent(out, indent);
        if (isDefault) out.append('*');
        out.append('[').append(key).append("] ");
        appendVariantValue(out, value, selectorVar, selectorIdx, allParams, indent);
        out.append('\n');
    }

    private void appendVariantValue(StringBuilder out, String laleinText,
                                    String selectorVar, int selectorIdx,
                                    Map<String, Parameter> allParams, int indent) {
        // Convert %{nested} to nested select expressions; convert %N$d / %d that
        // matches the selector's own index to "{ $selectorVar }".
        String s = laleinText;

        // Step 1: replace own positional spec (%N$d where N == selectorIdx, or bare %d if no positional) with {$selectorVar}
        StringBuilder afterSpecs = new StringBuilder();
        Matcher m = JAVA_INT_SPEC.matcher(s);
        while (m.find()) {
            String posStr = m.group(1);
            boolean isOwn;
            if (posStr == null) {
                // Bare %d — interpret as selector's own value
                isOwn = true;
            } else {
                isOwn = Integer.parseInt(posStr) == selectorIdx;
            }
            String repl = isOwn ? "{ $" + selectorVar + " }" : m.group();
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
