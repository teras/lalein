package com.panayotis.lalein;

import java.io.*;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class PropertiesLalein {
    private static final Pattern tag = Pattern.compile("%\\{(\\w+)}");

    public static Lalein fromResource(String resource) {
        try (InputStream is = PropertiesLalein.class.getResourceAsStream(resource)) {
            return fromStream(is);
        } catch (IOException e) {
            throw new LaleinException("Properties", "resource " + resource, e);
        }
    }

    public static Lalein fromString(String data) {
        try (Reader reader = new StringReader(data)) {
            return fromReader(reader);
        } catch (IOException e) {
            throw new LaleinException("Properties", "string data", e);
        }
    }

    public static Lalein fromFile(File data) {
        try (InputStream is = Files.newInputStream(data.toPath())) {
            return fromStream(is);
        } catch (IOException e) {
            throw new LaleinException("Properties", "file " + data.getAbsolutePath(), e);
        }
    }


    public static Lalein fromStream(InputStream data) {
        Properties properties = new Properties();
        try {
            properties.load(data);
        } catch (IOException e) {
            throw new LaleinException("Properties", data.getClass().getName(), e);
        }
        return fromProperties(properties);
    }

    public static Lalein fromReader(Reader data) {
        Properties properties = new Properties();
        try {
            properties.load(data);
        } catch (IOException e) {
            throw new LaleinException("Properties", data.getClass().getName(), e);
        }
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

    public static Properties toProperties(Lalein lalein) {
        Properties result = new Properties();
        for (Map.Entry<String, Translation> e : lalein.entries()) {
            String handler = e.getKey();
            Translation translation = e.getValue();
            Map<String, Parameter> params = translation.parameters;
            if (params == null || params.isEmpty()) {
                result.setProperty(handler, translation.format);
            } else {
                result.setProperty(handler, "%{" + params.keySet().iterator().next() + "}");
                for (Map.Entry<String, Parameter> pe : params.entrySet()) {
                    String base = handler + "." + pe.getKey() + ".";
                    Parameter p = pe.getValue();
                    result.setProperty(base + "i", String.valueOf(p.argumentIndex));
                    if (p.zero != null) result.setProperty(base + "z", p.zero);
                    if (p.one != null) result.setProperty(base + "o", p.one);
                    if (p.two != null) result.setProperty(base + "t", p.two);
                    if (p.few != null) result.setProperty(base + "f", p.few);
                    if (p.many != null) result.setProperty(base + "m", p.many);
                    if (p.other != null) result.setProperty(base + "r", p.other);
                }
            }
        }
        return result;
    }

    private static void findParams(String param, Properties properties, Map<String, Parameter> parameters, String handler) {
        if (param == null) {
            findEntryParam(properties.getProperty(handler), properties, parameters, handler);
        } else {
            String base = handler + "." + param + ".";
            findEntryParam(properties.getProperty(base + "z"), properties, parameters, handler);
            findEntryParam(properties.getProperty(base + "o"), properties, parameters, handler);
            findEntryParam(properties.getProperty(base + "t"), properties, parameters, handler);
            findEntryParam(properties.getProperty(base + "f"), properties, parameters, handler);
            findEntryParam(properties.getProperty(base + "m"), properties, parameters, handler);
            findEntryParam(properties.getProperty(base + "r"), properties, parameters, handler);
        }
    }

    private static void findEntryParam(String format, Properties properties, Map<String, Parameter> parameters, String handler) {
        if (format == null)
            return;
        Matcher matcher = tag.matcher(format);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!parameters.containsKey(name)) {
                String base = handler + "." + name + ".";
                parameters.put(name, new Parameter(
                        Integer.parseInt(properties.getProperty(base + "i")),
                        properties.getProperty(base + "z"),
                        properties.getProperty(base + "o"),
                        properties.getProperty(base + "t"),
                        properties.getProperty(base + "f"),
                        properties.getProperty(base + "m"),
                        properties.getProperty(base + "r")));
                findParams(name, properties, parameters, handler);
            }
        }
    }
}
