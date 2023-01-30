package com.panayotis.lalein;

import java.io.*;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.panayotis.lalein.PluralType.*;

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
        BiConsumer<String, Parameter> pluralTypes = (base, param) -> {
            result.setProperty(base + "i", String.valueOf(param.argumentIndex));
            if (param.zero != null) result.setProperty(base + ZERO.tag, param.zero);
            if (param.one != null) result.setProperty(base + ONE.tag, param.one);
            if (param.two != null) result.setProperty(base + TWO.tag, param.two);
            if (param.few != null) result.setProperty(base + FEW.tag, param.few);
            if (param.many != null) result.setProperty(base + MANY.tag, param.many);
            if (param.other != null) result.setProperty(base + OTHER.tag, param.other);
        };
        lalein.entries().forEach(e -> {
            Translation translation = e.getValue();
            Map<String, Parameter> params = translation.parameters;
            if (params == null || params.isEmpty())
                result.setProperty(e.getKey(), translation.format);
            else {
                result.setProperty(e.getKey(), "%{" + params.keySet().iterator().next() + "}");
                params.forEach((key, value) -> pluralTypes.accept(e.getKey() + "." + key + ".", value));
            }
        });
        return result;
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
