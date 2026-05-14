package com.panayotis.lalein;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
                    addParam(mData, "base", 1, handler, parameters);
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
        Set<String> tagKeys = new LinkedHashSet<>(value.keySet());
        tagKeys.remove(INDEX_KEY);
        String invalid = PluralType.findInvalidKey(tagKeys);
        if (invalid != null)
            throw new IllegalArgumentException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.put(name, new Parameter(index,
                (String) value.get(ZERO.tag),
                (String) value.get(ONE.tag),
                (String) value.get(TWO.tag),
                (String) value.get(FEW.tag),
                (String) value.get(MANY.tag),
                (String) value.get(OTHER.tag)));
    }
}
