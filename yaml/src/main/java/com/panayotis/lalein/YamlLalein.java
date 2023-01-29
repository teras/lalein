package com.panayotis.lalein;

import com.amihaiemil.eoyaml.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Iterator;
import java.util.Scanner;

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
        return DataConverter.toLalein(mapping,
                m -> () -> new Iterator<String>() {
                    private final Iterator<YamlNode> iterator = m.keys().iterator();

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public String next() {
                        return iterator.next().asScalar().value();
                    }
                },
                YamlMapping::values,
                YamlMapping::value,
                it -> it != null && it.type() == Node.SCALAR,
                it -> it != null && it.type() == Node.MAPPING,
                it -> it == null ? null : it.asScalar().value(),
                YamlNode::asMapping);
    }

    public static YamlMapping toYaml(Lalein lalein) {
        return DataConverter.fromLalein(lalein,
                Yaml::createYamlMappingBuilder,
                YamlMappingBuilder::build,
                YamlMappingBuilder::add,
                YamlMappingBuilder::add);
    }
}
