package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

@SuppressWarnings("unused")
public class JsonLalein {
    public static Lalein fromResource(String resource) throws IOException {
        return fromStream(JsonLalein.class.getResourceAsStream(resource));
    }

    public static Lalein fromString(String data) {
        return fromJson(Json.parse(data).asObject());
    }

    public static Lalein fromFile(File data) throws IOException {
        return fromJson(Json.parse(new InputStreamReader(Files.newInputStream(data.toPath()), StandardCharsets.UTF_8)).asObject());
    }

    public static Lalein fromWriter(Reader data) throws IOException {
        return fromJson(Json.parse(data).asObject());
    }

    public static Lalein fromStream(InputStream data) throws IOException {
        return fromJson(Json.parse(new InputStreamReader(data, StandardCharsets.UTF_8)).asObject());
    }

    public static Lalein fromJson(JsonObject data) {
        return DataConverter.toLalein(data,
                JsonObject::names,
                m -> () -> new Iterator<JsonValue>() {
                    private final Iterator<JsonObject.Member> iterator = m.iterator();

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public JsonValue next() {
                        return iterator.next().getValue();
                    }
                },
                JsonObject::get,
                i -> i != null && i.isString(),
                i -> i != null && i.isObject(),
                i -> i == null ? null : i.asString(),
                JsonValue::asObject
        );
    }


    public static JsonObject toJson(Lalein lalein) {
        return DataConverter.fromLalein(lalein,
                JsonObject::new,
                i -> i,
                (j, k, v) -> {
                    j.add(k, v);
                    return j;
                },
                (j, k, v) -> {
                    j.add(k, v);
                    return j;
                });
    }
}
