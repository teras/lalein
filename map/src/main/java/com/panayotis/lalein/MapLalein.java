package com.panayotis.lalein;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class MapLalein {

    public static Lalein fromMap(Map<String, Object> data) {
        //noinspection unchecked
        return DataConverter.toLalein(data,
                Map::keySet,
                Map::values,
                Map::get,
                i -> i instanceof String,
                i -> i instanceof Map,
                i -> (String) i,
                i -> (Map<String, Object>) i
        );
    }

    public static Map<String, Object> toMap(Lalein lalein) {
        return DataConverter.fromLalein(lalein,
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

}
