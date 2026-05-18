package com.panayotis.lalein;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.panayotis.lalein.PluralType.*;

class DataConverter {

    /** Reserved key inside a parameter map that explicitly declares its argument index. */
    private static final String INDEX_KEY = "i";

    /** Reserved key at the multi-parameter level that explicitly declares the master format template. */
    private static final String FORMAT_KEY = "format";

    static Map<String, Object> fromLalein(Lalein lalein) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Translation> e : lalein.entries()) {
            Translation translation = e.getValue();
            Map<String, Parameter> params = translation.parameters;
            if (params == null || params.isEmpty())
                result.put(e.getKey(), translation.format);
            else if (params.size() == 1)
                result.put(e.getKey(), getPlural(params.values().iterator().next()));
            else {
                Map<String, Object> multi = new LinkedHashMap<>();
                String firstKey = params.keySet().iterator().next();
                if (!("%{" + firstKey + "}").equals(translation.format))
                    multi.put(FORMAT_KEY, translation.format);
                int oldIdx = 0;
                for (Map.Entry<String, Parameter> pe : params.entrySet()) {
                    Parameter param = pe.getValue();
                    String prefix = oldIdx == param.argumentIndex ? "^" : "";
                    multi.put(prefix + pe.getKey(), getPlural(param));
                    oldIdx = param.argumentIndex;
                }
                result.put(e.getKey(), multi);
            }
        }
        return result;
    }

    private static Map<String, Object> getPlural(Parameter param) {
        Map<String, Object> o = new LinkedHashMap<>();
        if (param.zero != null) o.put(ZERO.tag, param.zero);
        if (param.one != null) o.put(ONE.tag, param.one);
        if (param.two != null) o.put(TWO.tag, param.two);
        if (param.few != null) o.put(FEW.tag, param.few);
        if (param.many != null) o.put(MANY.tag, param.many);
        if (param.other != null) o.put(OTHER.tag, param.other);
        if (param.custom != null) o.putAll(param.custom);
        return o;
    }

    static Lalein toLalein(Map<String, Object> mapping) {
        Map<String, Translation> translations = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : mapping.entrySet()) {
            String handler = e.getKey();
            Object data = e.getValue();
            String format;
            Map<String, Parameter> parameters;
            if (data instanceof String) {
                format = (String) data;
                parameters = null;
            } else {
                Map<String, Object> mData = asMap(data);
                parameters = new LinkedHashMap<>();
                if (allChildrenAreScalarOrIndex(mData)) {
                    format = "%{base}";
                    addParam(mData, "base", autoIndex(handler, mData), handler, parameters);
                } else {
                    Object explicitFormat = mData.get(FORMAT_KEY);
                    format = explicitFormat instanceof String ? (String) explicitFormat : null;
                    int previousIndex = 0;
                    for (Map.Entry<String, Object> ce : mData.entrySet()) {
                        String key = ce.getKey();
                        if (FORMAT_KEY.equals(key)) continue;
                        Object value = ce.getValue();
                        if (!(value instanceof Map))
                            throw new LaleinException("Waiting for a map with key " + key + " but no map found");
                        Map<String, Object> paramMap = asMap(value);
                        boolean caret = key.startsWith("^");
                        if (caret) key = key.substring(1);
                        if (format == null)
                            format = "%{" + key + "}";
                        Object explicit = paramMap.get(INDEX_KEY);
                        int index;
                        if (explicit != null)
                            index = toInt(explicit);
                        else if (caret)
                            index = previousIndex;
                        else
                            index = ++previousIndex;
                        previousIndex = index;
                        addParam(paramMap, key, index, handler, parameters);
                    }
                }
            }
            translations.put(handler, new Translation(format, parameters));
        }
        return new Lalein(translations);
    }

    private static final Pattern PRINTF = Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?([a-zA-Z%])");
    private static final String NUMERIC_CONV = "diouxXeEfgGaA";

    /**
     * Auto-detect the 1-based index of the argument that drives the plural
     * for a short-form parameter. Step 1: if all plural forms reference a
     * single unique positional numeric ref (e.g. %2$d), use it. Step 2: scan
     * the handler for the first numeric placeholder. Step 3: fallback to 1.
     * Explicit "i" keys always override (handled in addParam).
     */
    private static int autoIndex(String handler, Map<String, Object> mData) {
        int unique = 0;
        boolean ambiguous = false;
        outer:
        for (Object v : mData.values()) {
            if (!(v instanceof String)) continue;
            Matcher m = PRINTF.matcher((String) v);
            while (m.find()) {
                String pos = m.group(1);
                if (pos == null) continue;
                if (NUMERIC_CONV.indexOf(m.group(2).charAt(0)) < 0) continue;
                int n = Integer.parseInt(pos);
                if (unique == 0) unique = n;
                else if (unique != n) {
                    ambiguous = true;
                    break outer;
                }
            }
        }
        if (!ambiguous && unique > 0) return unique;
        Matcher m = PRINTF.matcher(handler);
        int implicit = 0;
        while (m.find()) {
            char conv = m.group(2).charAt(0);
            if (conv == '%' || conv == 'n') continue;
            String pos = m.group(1);
            int idx = pos != null ? Integer.parseInt(pos) : ++implicit;
            if (NUMERIC_CONV.indexOf(conv) >= 0) return idx;
        }
        return 1;
    }

    private static boolean allChildrenAreScalarOrIndex(Map<String, Object> data) {
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (INDEX_KEY.equals(e.getKey())) continue;
            if (!(e.getValue() instanceof String))
                return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static int toInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(o.toString());
    }

    private static void addParam(Map<String, Object> value, String name, int index, String handler, Map<String, Parameter> parameters) {
        Object explicit = value.get(INDEX_KEY);
        if (explicit != null) index = toInt(explicit);
        Map<String, String> custom = null;
        for (Map.Entry<String, Object> e : value.entrySet()) {
            String key = e.getKey();
            if (INDEX_KEY.equals(key)) continue;
            if (FORMAT_KEY.equals(key))
                throw new LaleinException("Reserved key 'format' cannot appear as a form name in parameter '" + name + "' of '" + handler + "'");
            if (PluralType.isPluralTag(key)) continue;
            if (!(e.getValue() instanceof String))
                throw new LaleinException("Value for key '" + key + "' in parameter '" + name + "' of '" + handler + "' must be a string");
            if (custom == null) custom = new LinkedHashMap<>();
            custom.put(key, (String) e.getValue());
        }
        parameters.put(name, new Parameter(index,
                (String) value.get(ZERO.tag),
                (String) value.get(ONE.tag),
                (String) value.get(TWO.tag),
                (String) value.get(FEW.tag),
                (String) value.get(MANY.tag),
                (String) value.get(OTHER.tag),
                custom));
    }
}
