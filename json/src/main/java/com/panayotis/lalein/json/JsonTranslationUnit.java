package com.panayotis.lalein.json;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.panayotis.lalein.LaleinException;
import com.panayotis.lalein.Parameter;
import com.panayotis.lalein.PluralType;
import com.panayotis.lalein.TranslationUnit;

import java.util.ArrayList;
import java.util.List;

import static com.panayotis.lalein.PluralType.*;

class JsonTranslationUnit implements TranslationUnit {
    private final String handler;
    private final String format;
    private final List<Parameter> parameters;

    JsonTranslationUnit(String handler, String format) {
        this.handler = handler;
        this.format = format;
        parameters = null;
    }

    JsonTranslationUnit(String handler, JsonObject data) {
        this.handler = handler;
        this.parameters = new ArrayList<>();

        if (allChildrenAreStrings(data)) {
            // Simple version: only one parameter
            this.format = "%{base}";
            addParam(data, "base", 1);
        } else {
            // More complex version: parameters could be more than one
            List<String> params = data.names();
            this.format = "%{" + params.get(0) + "}";
            int previousIndex = 0;
            for (String name : params) {
                int index;
                String baseName;
                if (name.startsWith("^")) {
                    baseName = name.substring(1);
                    index = previousIndex;
                } else {
                    baseName = name;
                    index = ++previousIndex;
                }
                addParam(data.get(name), baseName, index);
            }
        }
    }

    @Override
    public String getHandler() {
        return handler;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public Iterable<Parameter> getParameters() {
        return parameters;
    }

    private void addParam(JsonValue value, String name, int index) {
        if (!value.isObject())
            throw new LaleinException("Wrong JSON type of parameter " + name + " for handler '" + handler + "'");
        String invalid = PluralType.findInvalidKey(value.asObject().names());
        if (invalid != null)
            throw new LaleinException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.add(getParameter(value.asObject(), name, index));
    }

    private Parameter getParameter(JsonObject data, String name, int index) {
        return new Parameter(name, index,
                getValue(data, ZERO, index),
                getValue(data, ONE, index),
                getValue(data, TWO, index),
                getValue(data, FEW, index),
                getValue(data, MANY, index),
                getValue(data, OTHER, index));
    }

    private String getValue(JsonObject data, PluralType pluralEnum, int index) {
        JsonValue value = data.get(pluralEnum.tag);
        if (value == null)
            return null;
        if (!value.isString())
            throw new LaleinException("Wrong JSON type of parameter with plural " + pluralEnum.name().toLowerCase() + " at index #" + index + " for handler '" + handler + "'");
        return value.asString();
    }

    private boolean allChildrenAreStrings(JsonObject data) {
        for (String key : data.names())
            if (!data.get(key).isString())
                return false;
        return true;
    }
}
