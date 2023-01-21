package com.panayotis.lalein.yaml;

import com.panayotis.lalein.Parameter;
import com.panayotis.lalein.TranslationUnit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PropertiesTranslationUnit implements TranslationUnit {
    private static final Pattern tag = Pattern.compile("%\\{(\\w+)}");

    private final String handler;
    private final String format;
    private final Collection<Parameter> parameters;

    private void findParams(String param, Properties properties, Map<String, Parameter> parameters) {
        if (param == null) {
            findEntryParam(properties.getProperty(handler), properties, parameters);
        } else {
            findEntryParam(properties.getProperty(handler + "." + param + ".z"), properties, parameters);
            findEntryParam(properties.getProperty(handler + "." + param + ".o"), properties, parameters);
            findEntryParam(properties.getProperty(handler + "." + param + ".t"), properties, parameters);
            findEntryParam(properties.getProperty(handler + "." + param + ".f"), properties, parameters);
            findEntryParam(properties.getProperty(handler + "." + param + ".m"), properties, parameters);
            findEntryParam(properties.getProperty(handler + "." + param + ".r"), properties, parameters);
        }
    }

    private void findEntryParam(String format, Properties properties, Map<String, Parameter> parameters) {
        if (format == null)
            return;
        Matcher matcher = tag.matcher(format);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!parameters.containsKey(name)) {
                parameters.put(name, new Parameter(name,
                        Integer.parseInt(properties.getProperty(handler + "." + name + ".i")),
                        properties.getProperty(handler + "." + name + ".z"),
                        properties.getProperty(handler + "." + name + ".o"),
                        properties.getProperty(handler + "." + name + ".t"),
                        properties.getProperty(handler + "." + name + ".f"),
                        properties.getProperty(handler + "." + name + ".m"),
                        properties.getProperty(handler + "." + name + ".r")));
                findParams(name, properties, parameters);
            }
        }
    }

    PropertiesTranslationUnit(String unit, Properties properties) {
        this.handler = unit;
        Map<String, Parameter> parameters = new LinkedHashMap<>();
        findParams(null, properties, parameters);
        this.format = properties.getProperty(this.handler);
        this.parameters = parameters.values();
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
}
