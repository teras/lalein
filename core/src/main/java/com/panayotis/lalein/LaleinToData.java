package com.panayotis.lalein;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.panayotis.lalein.PluralType.*;

class LaleinToData {
    static <B, M> M convert(Lalein lalein, Supplier<B> mB, Function<B, M> conv,
                            TriFunction<B, String, String> scalarAdd,
                            TriFunction<B, String, M> mapAdd) {
        B result = mB.get();
        Function<Parameter, M> pluralTypes = (param) -> {
            B o = mB.get();
            if (param.zero != null) o = scalarAdd.apply(o, ZERO.tag, param.zero);
            if (param.one != null) o = scalarAdd.apply(o, ONE.tag, param.one);
            if (param.two != null) o = scalarAdd.apply(o, TWO.tag, param.two);
            if (param.few != null) o = scalarAdd.apply(o, FEW.tag, param.few);
            if (param.many != null) o = scalarAdd.apply(o, MANY.tag, param.many);
            if (param.other != null) o = scalarAdd.apply(o, OTHER.tag, param.other);
            return conv.apply(o);
        };
        for (Map.Entry<String, Translation> e : lalein.entries()) {
            Translation translation = e.getValue();
            Map<String, Parameter> params = translation.parameters;
            if (params == null || params.isEmpty())
                result = scalarAdd.apply(result, e.getKey(), translation.format);
            else if (params.size() == 1)
                result = mapAdd.apply(result, e.getKey(), pluralTypes.apply(params.values().iterator().next()));
            else {
                B multiParams = mB.get();
                int oldIdx = 0;
                for (String name : params.keySet()) {
                    Parameter param = params.get(name);
                    String prefix = oldIdx == param.argumentIndex ? "^" : "";
                    multiParams = mapAdd.apply(multiParams, prefix + name, pluralTypes.apply(param));
                    oldIdx = param.argumentIndex;
                }
                result = mapAdd.apply(result, e.getKey(), conv.apply(multiParams));
            }
        }
        return conv.apply(result);
    }
}

interface TriFunction<R, K, V> {
    R apply(R data, K key, V value);
}
