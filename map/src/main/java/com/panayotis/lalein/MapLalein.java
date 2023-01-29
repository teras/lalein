package com.panayotis.lalein;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.panayotis.lalein.PluralType.*;

@SuppressWarnings("unused")
public class MapLalein {

    public static Lalein fromMap(Map<String, ?> data) {
        Map<String, Translation> translations = new LinkedHashMap<>();
        data.forEach((handler, value) -> {
            Translation translation;
            if (value == null)
                throw new NullPointerException("Unable to find data for handler " + handler);
            else if (value instanceof String)
                translation = new Translation((String) value, null);
            else if (value instanceof Map) {
                Map<String, ?> pData = asStringMap((Map<?, ?>) value);
                Collection<String> pNames = pData.keySet();
                Map<String, Parameter> parameters;
                String format;
                if (pNames.isEmpty()) {
                    parameters = null;
                    format = "";
                } else {
                    parameters = new LinkedHashMap<>();
                    if (findNonString(pData.values()) == null) {
                        // Simple version: only one parameter
                        format = "%{base}";
                        addParam(pData, "base", 1, handler, parameters);
                    } else {
                        // More complex version: parameters could be more than one
                        format = "%{" + pNames.iterator().next() + "}";
                        int previousIndex = 0;
                        for (String name : pNames) {
                            int index;
                            String baseName;
                            if (name.startsWith("^")) {
                                if (previousIndex == 0)
                                    previousIndex++;
                                baseName = name.substring(1);
                                index = previousIndex;
                            } else {
                                baseName = name;
                                index = ++previousIndex;
                            }
                            addParam(pData.get(name), baseName, index, handler, parameters);
                        }
                    }
                }
                translation = new Translation(format, parameters);
            } else
                throw new LaleinException("Invalid data type, either String or Map expected, found " + value.getClass().getName());
            translations.put(handler, translation);
        });
        return new Lalein(translations);
    }

    public static Map<String, ?> toMap(Lalein lalein) {
        return LaleinToData.convert(lalein,
                () -> new LinkedHashMap<String, Object>(),
                i -> i,
                (j, k, v) -> {
                    j.put(k, v);
                    return j;
                },
                (j, k, v) -> {
                    j.put(k, v);
                    return j;
                });
    }


    private static Map<String, ?> asStringMap(Map<?, ?> input) {
        Object error = findNonString(input.keySet());
        if (error != null)
            throw new LaleinException("All keys should be Strings, found key '" + error + "' instead");
        //noinspection unchecked
        return (Map<String, ?>) input;
    }

    private static Object findNonString(Collection<?> data) {
        for (Object item : data)
            if (!(item instanceof String))
                return item;
        return null;
    }

    private static void addParam(Object input, String name, int index, String handler, Map<String, Parameter> parameters) {
        if (!(input instanceof Map))
            throw new LaleinException("Wrong type of parameter " + name + " for handler '" + handler + "'");
        Map<String, ?> value = asStringMap((Map<?, ?>) input);
        String invalid = PluralType.findInvalidKey(value.keySet());
        if (invalid != null)
            throw new LaleinException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.put(name, getParameter(value, index, handler));
    }

    private static Parameter getParameter(Map<String, ?> data, int index, String handler) {
        return new Parameter(index,
                getValue(data, ZERO, index, handler),
                getValue(data, ONE, index, handler),
                getValue(data, TWO, index, handler),
                getValue(data, FEW, index, handler),
                getValue(data, MANY, index, handler),
                getValue(data, OTHER, index, handler));
    }

    private static String getValue(Map<String, ?> data, PluralType pluralEnum, int index, String handler) {
        Object value = data.get(pluralEnum.tag);
        if (value == null)
            return null;
        if (!(value instanceof String))
            throw new LaleinException("Wrong type of parameter with plural " + pluralEnum.name().toLowerCase() + " at index #" + index + " for handler '" + handler + "'");
        return (String) value;
    }
}
