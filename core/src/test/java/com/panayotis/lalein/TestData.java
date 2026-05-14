package com.panayotis.lalein;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers for constructing Lalein/Translation/Parameter without going through a backend parser.
 * Lives in the same package so it can use the package-private constructors.
 */
class TestData {

    static Lalein lalein(String key, Translation value) {
        Map<String, Translation> m = new LinkedHashMap<>();
        m.put(key, value);
        return new Lalein(m);
    }

    static Builder builder() {
        return new Builder();
    }

    static Parameter param(int idx, String z, String o, String t, String f, String m, String r) {
        return new Parameter(idx, z, o, t, f, m, r);
    }

    static Translation plain(String text) {
        return new Translation(text, null);
    }

    static class Builder {
        final Map<String, Translation> data = new LinkedHashMap<>();

        Builder add(String key, String text) {
            data.put(key, new Translation(text, null));
            return this;
        }

        Builder add(String key, String format, Map<String, Parameter> params) {
            data.put(key, new Translation(format, params));
            return this;
        }

        Lalein build() {
            return new Lalein(data);
        }
    }

    static Map<String, Parameter> params(Object... kvs) {
        if (kvs.length % 2 != 0) throw new IllegalArgumentException("Need pairs");
        Map<String, Parameter> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2)
            m.put((String) kvs[i], (Parameter) kvs[i + 1]);
        return m;
    }
}
