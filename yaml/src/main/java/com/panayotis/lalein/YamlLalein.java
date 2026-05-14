package com.panayotis.lalein;

import com.amihaiemil.eoyaml.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

@SuppressWarnings("unused")
public class YamlLalein {
    public static Lalein fromResource(String resource) {
        try (InputStream is = YamlLalein.class.getResourceAsStream(resource)) {
            return fromStream(is);
        } catch (IOException e) {
            throw new LaleinException("YAML", "resource " + resource, e);
        }
    }

    public static Lalein fromString(String data) {
        return fromYamlInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromFile(File data) {
        try {
            return fromYamlInput(Yaml.createYamlInput(data));
        } catch (IOException e) {
            throw new LaleinException("YAML", "file " + data.getAbsolutePath(), e);
        }
    }

    public static Lalein fromStream(InputStream data) {
        return fromYamlInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromReader(Reader data) {
        Scanner scanner = new Scanner(data);
        scanner.useDelimiter("\\A");
        String fileAsString = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        return fromString(fileAsString);
    }

    public static Lalein fromYamlInput(YamlInput input) {
        try {
            return fromYaml(input.readYamlMapping());
        } catch (IOException e) {
            throw new LaleinException("YAML", input.getClass().getName(), e);
        }
    }

    public static Lalein fromYaml(YamlMapping mapping) {
        return DataConverter.toLalein(yamlToMap(mapping));
    }

    public static YamlMapping toYaml(Lalein lalein) {
        return mapToYaml(DataConverter.fromLalein(lalein));
    }

    private static Map<String, Object> yamlToMap(YamlMapping mapping) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (YamlNode keyNode : mapping.keys()) {
            String key = keyNode.asScalar().value();
            YamlNode value = mapping.value(key);
            if (value == null) continue;
            if (value.type() == Node.SCALAR)
                m.put(key, value.asScalar().value());
            else if (value.type() == Node.MAPPING)
                m.put(key, yamlToMap(value.asMapping()));
            else
                throw new LaleinException("Unexpected YAML node type for key " + key);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static YamlMapping mapToYaml(Map<String, Object> m) {
        YamlMappingBuilder builder = Yaml.createYamlMappingBuilder();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String)
                builder = builder.add(e.getKey(), (String) v);
            else
                builder = builder.add(e.getKey(), mapToYaml((Map<String, Object>) v));
        }
        return builder.build();
    }
}
