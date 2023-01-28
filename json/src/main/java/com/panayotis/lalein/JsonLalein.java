package com.panayotis.lalein;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.panayotis.lalein.PluralType.*;

public class JsonLalein {
    public static Lalein fromResource(String resource) throws IOException {
        return fromStream(JsonLalein.class.getResourceAsStream(resource));
    }

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

    public static Lalein fromJson(JsonObject data) {
        Map<String, Translation> translations = new LinkedHashMap<>();
        for (JsonObject.Member next : data) {
            String handler = next.getName();
            JsonValue value = next.getValue();

            Translation translation;
            if (value.isString())
                translation = new Translation(value.asString(), null);
            else {
                JsonObject pData = value.asObject();
                List<String> pNames = pData.names();
                Map<String, Parameter> parameters;
                String format;
                if (pNames.isEmpty()) {
                    parameters = null;
                    format = "";
                } else {
                    parameters = new LinkedHashMap<>();
                    if (allChildrenAreStrings(pData)) {
                        // Simple version: only one parameter
                        format = "%{base}";
                        addParam(pData, "base", 1, handler, parameters);
                    } else {
                        // More complex version: parameters could be more than one
                        format = "%{" + pNames.get(0) + "}";
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
            }
            translations.put(handler, translation);
        }
        return new Lalein(translations);
    }

    private static JsonObject asObject(JsonValue value) {
        if (!value.isObject())
            throw new LaleinException("The root JSON element is not an object");
        return value.asObject();
    }

    private static void addParam(JsonValue value, String name, int index, String handler, Map<String, Parameter> parameters) {
        if (!value.isObject())
            throw new LaleinException("Wrong JSON type of parameter " + name + " for handler '" + handler + "'");
        String invalid = PluralType.findInvalidKey(value.asObject().names());
        if (invalid != null)
            throw new LaleinException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.put(name, getParameter(value.asObject(), index, handler));
    }

    private static Parameter getParameter(JsonObject data, int index, String handler) {
        return new Parameter(index,
                getValue(data, ZERO, index, handler),
                getValue(data, ONE, index, handler),
                getValue(data, TWO, index, handler),
                getValue(data, FEW, index, handler),
                getValue(data, MANY, index, handler),
                getValue(data, OTHER, index, handler));
    }

    private static String getValue(JsonObject data, PluralType pluralEnum, int index, String handler) {
        JsonValue value = data.get(pluralEnum.tag);
        if (value == null)
            return null;
        if (!value.isString())
            throw new LaleinException("Wrong JSON type of parameter with plural " + pluralEnum.name().toLowerCase() + " at index #" + index + " for handler '" + handler + "'");
        return value.asString();
    }

    private static boolean allChildrenAreStrings(JsonObject data) {
        for (String key : data.names())
            if (!data.get(key).isString())
                return false;
        return true;
    }
}
