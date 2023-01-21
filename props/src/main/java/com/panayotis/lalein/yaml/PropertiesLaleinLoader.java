package com.panayotis.lalein.yaml;

import com.panayotis.lalein.Lalein;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;

public class PropertiesLaleinLoader {
    public static Lalein fromResource(String resource) throws IOException {
        return fromStream(PropertiesLaleinLoader.class.getResourceAsStream(resource));
    }

    public static Lalein fromString(String data) throws IOException {
        return fromReader(new StringReader(data));
    }

    public static Lalein fromFile(File data) throws IOException {
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


    public static Lalein fromProperties(Properties mapping) {
        Iterator<String> keys = mapping
                .stringPropertyNames()
                .stream()
                .filter(it -> !it.contains(".")).iterator();
        Iterable<PropertiesTranslationUnit> provider = () -> new Iterator<PropertiesTranslationUnit>() {

            @Override
            public boolean hasNext() {
                return keys.hasNext();
            }

            @Override
            public PropertiesTranslationUnit next() {
                return new PropertiesTranslationUnit(keys.next(), mapping);
            }
        };
        return new Lalein(provider);
    }
}
