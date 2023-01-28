package com.panayotis.lalein;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class PropertiesLalein {
    private static final Pattern tag = Pattern.compile("%\\{(\\w+)}");

    public static Lalein fromResource(String resource) throws IOException {
        return fromStream(PropertiesLalein.class.getResourceAsStream(resource));
    }

    public static Lalein fromString(String data) throws IOException {
        return fromReader(new StringReader(data));
    }

    public static Lalein fromFile(File data) throws IOException {
        //noinspection IOStreamConstructor
        return fromStream(new FileInputStream(data));
    }


    public static Lalein fromStream(InputStream data) throws IOException {
        Properties properties = new Properties();
        properties.load(data);
        return fromProperties(properties);
    }

    public static Lalein fromReader(Reader data) throws IOException {
        Properties properties = new Properties();
        properties.load(data);
        return fromProperties(properties);
    }

    public static Lalein fromProperties(Properties data) {
        Map<String, Translation> translations = new LinkedHashMap<>();
        for (String handler : data.stringPropertyNames()) {
            if (handler.contains("."))
                continue;
            Map<String, Parameter> parameters = new LinkedHashMap<>();
            findParams(null, data, parameters, handler);
            translations.put(handler, new Translation(data.getProperty(handler), parameters.isEmpty() ? null : parameters));
        }
        return new Lalein(translations);
    }

    private static void findParams(String param, Properties properties, Map<String, Parameter> parameters, String handler) {
        if (param == null) {
            findEntryParam(properties.getProperty(handler), properties, parameters, handler);
        } else {
            findEntryParam(properties.getProperty(handler + "." + param + ".z"), properties, parameters, handler);
            findEntryParam(properties.getProperty(handler + "." + param + ".o"), properties, parameters, handler);
            findEntryParam(properties.getProperty(handler + "." + param + ".t"), properties, parameters, handler);
            findEntryParam(properties.getProperty(handler + "." + param + ".f"), properties, parameters, handler);
            findEntryParam(properties.getProperty(handler + "." + param + ".m"), properties, parameters, handler);
            findEntryParam(properties.getProperty(handler + "." + param + ".r"), properties, parameters, handler);
        }
    }

    private static void findEntryParam(String format, Properties properties, Map<String, Parameter> parameters, String handler) {
        if (format == null)
            return;
        Matcher matcher = tag.matcher(format);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!parameters.containsKey(name)) {
                parameters.put(name, new Parameter(Integer.parseInt(properties.getProperty(handler + "." + name + ".i")),
                        properties.getProperty(handler + "." + name + ".z"),
                        properties.getProperty(handler + "." + name + ".o"),
                        properties.getProperty(handler + "." + name + ".t"),
                        properties.getProperty(handler + "." + name + ".f"),
                        properties.getProperty(handler + "." + name + ".m"),
                        properties.getProperty(handler + "." + name + ".r")));
                findParams(name, properties, parameters, handler);
            }
        }
    }
}
