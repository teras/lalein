package com.panayotis.lalein;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.panayotis.lalein.PluralType.*;

class DataConverter {
    static <B, M> M fromLalein(
            Lalein lalein,
            Supplier<B> mB,
            Function<B, M> conv,
            TriFunction<B, String, String> scalarAdd,
            TriFunction<B, String, M> mapAdd) {
        B result = mB.get();
        for (Map.Entry<String, Translation> e : lalein.entries()) {
            Translation translation = e.getValue();
            Map<String, Parameter> params = translation.parameters;
            if (params == null || params.isEmpty())
                result = scalarAdd.apply(result, e.getKey(), translation.format);
            else if (params.size() == 1)
                result = mapAdd.apply(result, e.getKey(), getPlural(params.values().iterator().next(), mB, conv, scalarAdd));
            else {
                B multiParams = mB.get();
                int oldIdx = 0;
                for (String name : params.keySet()) {
                    Parameter param = params.get(name);
                    String prefix = oldIdx == param.argumentIndex ? "^" : "";
                    multiParams = mapAdd.apply(multiParams, prefix + name, getPlural(param, mB, conv, scalarAdd));
                    oldIdx = param.argumentIndex;
                }
                result = mapAdd.apply(result, e.getKey(), conv.apply(multiParams));
            }
        }
        return conv.apply(result);
    }

    private static <B, M> M getPlural(
            Parameter param,
            Supplier<B> mB,
            Function<B, M> conv,
            TriFunction<B, String, String> scalarAdd) {
        B o = mB.get();
        if (param.zero != null) o = scalarAdd.apply(o, ZERO.tag, param.zero);
        if (param.one != null) o = scalarAdd.apply(o, ONE.tag, param.one);
        if (param.two != null) o = scalarAdd.apply(o, TWO.tag, param.two);
        if (param.few != null) o = scalarAdd.apply(o, FEW.tag, param.few);
        if (param.many != null) o = scalarAdd.apply(o, MANY.tag, param.many);
        if (param.other != null) o = scalarAdd.apply(o, OTHER.tag, param.other);
        return conv.apply(o);
    }

    static <M, V> Lalein toLalein(
            M mapping,
            Function<M, Iterable<String>> keys,
            Function<M, Iterable<V>> values,
            BiFunction<M, String, V> findValue,
            Predicate<V> isString,
            Predicate<V> isMap,
            Function<V, String> asString,
            Function<V, M> asMap) {
        Map<String, Translation> translations = new LinkedHashMap<>();
        for (String handler : keys.apply(mapping)) {
            V data = findValue.apply(mapping, handler);
            String format;
            Map<String, Parameter> parameters;
            if (isString.test(data)) {
                format = asString.apply(data);
                parameters = null;
            } else {
                M mData = asMap.apply(data);
                parameters = new LinkedHashMap<>();
                if (allChildrenAreStrings(mData, values, isString)) {
                    // Simple version: only one parameter
                    format = "%{base}";
                    addParam(mData, keys, findValue, asString, "base", 1, handler, parameters);
                } else {
                    // More complex version: parameters could be more than one
                    format = null;
                    int previousIndex = 0;
                    for (String key : keys.apply(mData)) {
                        V value = findValue.apply(mData, key);
                        // the format is the first key
                        if (format == null)
                            format = "%{" + key + "}";
                        int index;
                        if (key.startsWith("^")) {
                            key = key.substring(1);
                            index = previousIndex;
                        } else
                            index = ++previousIndex;
                        if (isMap.test(value))
                            addParam(asMap.apply(value), keys, findValue, asString, key, index, handler, parameters);
                        else
                            throw new LaleinException("Waiting for a map with key " + key + " but no map found");
                    }
                }
            }
            translations.put(handler, new Translation(format, parameters));
        }
        return new Lalein(translations);
    }

    private static <M, V> boolean allChildrenAreStrings(
            M data,
            Function<M, Iterable<V>> values,
            Predicate<V> isString) {
        for (V value : values.apply(data))
            if (!isString.test(value))
                return false;
        return true;
    }

    private static <M, N, V> void addParam(
            M value,
            Function<M, Iterable<String>> keys,
            BiFunction<M, String, V> findValue,
            Function<V, String> asString,
            String name,
            int index,
            String handler,
            Map<String, Parameter> parameters) {
        String invalid = PluralType.findInvalidKey(keys.apply(value));
        if (invalid != null)
            throw new IllegalArgumentException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.put(name, getParameter(value, findValue, asString, index));
    }

    private static <M, V> Parameter getParameter(
            M data,
            BiFunction<M, String, V> findValue,
            Function<V, String> asString,
            int index) {
        return new Parameter(index,
                asString.apply(findValue.apply(data, ZERO.tag)),
                asString.apply(findValue.apply(data, ONE.tag)),
                asString.apply(findValue.apply(data, TWO.tag)),
                asString.apply(findValue.apply(data, FEW.tag)),
                asString.apply(findValue.apply(data, MANY.tag)),
                asString.apply(findValue.apply(data, OTHER.tag)));
    }
}

