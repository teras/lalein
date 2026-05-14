package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class JsonLalein {
    public static Lalein fromResource(String resource) {
        try (InputStream is = JsonLalein.class.getResourceAsStream(resource)) {
            return fromStream(is);
        } catch (IOException e) {
            throw new LaleinException("JSON", "resource " + resource, e);
        }
    }

    public static Lalein fromString(String data) {
        return fromJson(Json.parse(data).asObject());
    }

    public static Lalein fromFile(File data) {
        try (Reader reader = new FileReader(data)) {
            return fromJson(Json.parse(reader).asObject());
        } catch (IOException e) {
            throw new LaleinException("JSON", "file " + data.getAbsolutePath(), e);
        }
    }

    public static Lalein fromReader(Reader data) {
        try {
            return fromJson(Json.parse(data).asObject());
        } catch (IOException e) {
            throw new LaleinException("JSON", data.getClass().getName(), e);
        }
    }

    public static Lalein fromStream(InputStream data) {
        try {
            return fromJson(Json.parse(new InputStreamReader(data, StandardCharsets.UTF_8)).asObject());
        } catch (IOException e) {
            throw new LaleinException("JSON", data.getClass().getName(), e);
        }
    }

    public static Lalein fromJson(JsonObject data) {
        return DataConverter.toLalein(jsonToMap(data));
    }

    public static JsonObject toJson(Lalein lalein) {
        return mapToJson(DataConverter.fromLalein(lalein));
    }

    private static Map<String, Object> jsonToMap(JsonObject obj) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (JsonObject.Member member : obj) {
            JsonValue v = member.getValue();
            if (v.isString())
                m.put(member.getName(), v.asString());
            else if (v.isObject())
                m.put(member.getName(), jsonToMap(v.asObject()));
            else
                throw new LaleinException("Unexpected JSON value type for key " + member.getName());
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static JsonObject mapToJson(Map<String, Object> m) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String)
                obj.add(e.getKey(), (String) v);
            else
                obj.add(e.getKey(), mapToJson((Map<String, Object>) v));
        }
        return obj;
    }
}
