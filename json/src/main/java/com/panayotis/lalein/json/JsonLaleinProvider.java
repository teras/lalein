package com.panayotis.lalein.json;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.panayotis.lalein.Lalein;
import com.panayotis.lalein.LaleinException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

@SuppressWarnings("unused")
public class JsonLaleinProvider {
    public static Lalein fromString(String data) {
        return fromJson(asObject(Json.parse(data)));
    }

    public static Lalein fromFile(File data) throws IOException {
        return fromJson(asObject(Json.parse(new InputStreamReader(Files.newInputStream(data.toPath()), StandardCharsets.UTF_8))));
    }

    public static Lalein fromWriter(Reader data) throws IOException {
        return fromJson(asObject(Json.parse(data)));
    }

    public static Lalein fromStream(InputStream data) throws IOException {
        return fromJson(asObject(Json.parse(new InputStreamReader(data, StandardCharsets.UTF_8))));
    }

    public static Lalein fromJson(JsonObject value) {
        Iterator<JsonObject.Member> members = value.iterator();
        Iterable<JsonTranslationUnit> provider = () -> new Iterator<JsonTranslationUnit>() {
            @Override
            public boolean hasNext() {
                return members.hasNext();
            }

            @Override
            public JsonTranslationUnit next() {
                JsonObject.Member next = members.next();
                String handler = next.getName();
                JsonValue handlerValue = next.getValue();
                if (handlerValue.isString())
                    return new JsonTranslationUnit(handler, handlerValue.asString());
                else if (handlerValue.isObject())
                    return new JsonTranslationUnit(handler, handlerValue.asObject());
                else
                    throw new LaleinException("Invalid value of handler " + handler);
            }
        };
        return new Lalein(provider);
    }

    private static JsonObject asObject(JsonValue value) {
        if (!value.isObject())
            throw new LaleinException("The root JSON element is not an object");
        return value.asObject();
    }
}
