package com.panayotis.lalein;

import com.amihaiemil.eoyaml.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.panayotis.lalein.PluralType.*;

@SuppressWarnings("unused")
public class YamlLalein {
    public static Lalein fromResource(String resource) throws IOException {
        return fromStream(YamlLalein.class.getResourceAsStream(resource));
    }

    public static Lalein fromString(String data) throws IOException {
        return fromYamlInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromFile(File data) throws IOException {
        return fromYamlInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromStream(InputStream data) throws IOException {
        return fromYamlInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromReader(Reader data) throws IOException {
        Scanner scanner = new Scanner(data);
        scanner.useDelimiter("\\A");
        String fileAsString = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        return fromString(fileAsString);
    }

    public static Lalein fromYamlInput(YamlInput input) throws IOException {
        return fromYaml(input.readYamlMapping());
    }

    public static Lalein fromYaml(YamlMapping mapping) {
        Map<String, Translation> translations = new LinkedHashMap<>();
        for (YamlNode node : mapping.keys()) {
            YamlNode data = mapping.value(node);
            String handler = asString(node);
            String format;
            Map<String, Parameter> parameters;
            if (data.type() == Node.SCALAR) {
                format = asString(data);
                parameters = null;
            } else {
                YamlMapping mData = data.asMapping();
                parameters = new LinkedHashMap<>();
                if (allChildrenAreStrings(mData.asMapping())) {
                    // Simple version: only one parameter
                    format = "%{base}";
                    addParam(mData, "base", 1, handler, parameters);
                } else {
                    // More complex version: parameters could be more than one
                    List<YamlNode> params = new ArrayList<>(mData.keys());
                    format = "%{" + asString(params.get(0)) + "}";
                    int previousIndex = 0;
                    for (YamlNode key : params) {
                        String name = asString(key);

                        int index;
                        String baseName;
                        if (name.startsWith("^")) {
                            baseName = name.substring(1);
                            index = previousIndex;
                        } else {
                            baseName = name;
                            index = ++previousIndex;
                        }
                        addParam(mData.yamlMapping(key), baseName, index, handler, parameters);
                    }
                }
            }
            translations.put(handler, new Translation(format, parameters));
        }
        return new Lalein(translations);
    }

    public static YamlMapping toYaml(Lalein lalein) {
        return LaleinToData.convert(lalein,
                Yaml::createYamlMappingBuilder,
                YamlMappingBuilder::build,
                YamlMappingBuilder::add,
                YamlMappingBuilder::add);
    }

    private static void addParam(YamlMapping value, String name, int index, String handler, Map<String, Parameter> parameters) {
        String invalid = PluralType.findInvalidKey(value
                .keys()
                .stream()
                .map(YamlLalein::asString)
                .collect(Collectors.toList()));
        if (invalid != null)
            throw new IllegalArgumentException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.put(name, getParameter(value, index));
    }

    private static Parameter getParameter(YamlMapping data, int index) {
        return new Parameter(index,
                data.string(ZERO.tag),
                data.string(ONE.tag),
                data.string(TWO.tag),
                data.string(FEW.tag),
                data.string(MANY.tag),
                data.string(OTHER.tag));
    }

    private static boolean allChildrenAreStrings(YamlMapping data) {
        for (YamlNode value : data.values())
            if (value.type() != Node.SCALAR)
                return false;
        return true;
    }

    private static String asString(YamlNode node) {
        if (node.type() != Node.SCALAR)
            throw new LaleinException("invalid yaml type " + node.type() + ": a scalar is expected pfr node " + node);
        return node.asScalar().value();
    }
}
