package com.panayotis.lalein;

import java.util.ArrayList;
import java.util.Collection;

public class TranslationInfo {
    private final String handler;
    private final String format;
    private final Collection<ParameterInfo> parameters = new ArrayList<>();

    TranslationInfo(String handler, Translation translation) {
        this.handler = handler;
        this.format = translation.format;
        if (translation.parameters != null)
            translation.parameters.forEach((key, value) -> parameters.add(new ParameterInfo(this, key, value)));
    }

    public String getHandler() {
        return handler;
    }

    public String getFormat() {
        return format;
    }

    public int getParameterCount() {
        return parameters.size();
    }

    public Iterable<ParameterInfo> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return handler;
    }
}
