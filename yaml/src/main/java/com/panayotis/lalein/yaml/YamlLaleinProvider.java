package com.panayotis.lalein.yaml;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlInput;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlNode;
import com.panayotis.lalein.Lalein;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Iterator;
import java.util.Scanner;

@SuppressWarnings("unused")
public class YamlLaleinProvider {
    public static Lalein fromString(String data) throws IOException {
        return fromInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromFile(File data) throws IOException {
        return fromInput(Yaml.createYamlInput(data));
    }


    public static Lalein fromStream(InputStream data) throws IOException {
        return fromInput(Yaml.createYamlInput(data));
    }

    public static Lalein fromReader(Reader data) throws IOException {
        Scanner scanner = new Scanner(data);
        scanner.useDelimiter("\\A");
        String fileAsString = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        return fromString(fileAsString);
    }

    public static Lalein fromInput(YamlInput input) throws IOException {
        return fromMapping(input.readYamlMapping());
    }

    public static Lalein fromMapping(YamlMapping mapping) {
        Iterable<YamlTranslationUnit> provider = () -> new Iterator<YamlTranslationUnit>() {
            private final Iterator<YamlNode> members = mapping.keys().iterator();

            @Override
            public boolean hasNext() {
                return members.hasNext();
            }

            @Override
            public YamlTranslationUnit next() {
                YamlNode key = members.next();
                return new YamlTranslationUnit(key, mapping.value(key));
            }
        };
        return new Lalein(provider);
    }
}
