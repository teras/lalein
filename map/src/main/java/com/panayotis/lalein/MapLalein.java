package com.panayotis.lalein;

import java.util.Map;

@SuppressWarnings("unused")
public class MapLalein {

    public static Lalein fromMap(Map<String, Object> data) {
        return DataConverter.toLalein(data);
    }

    public static Map<String, Object> toMap(Lalein lalein) {
        return DataConverter.fromLalein(lalein);
    }
}
