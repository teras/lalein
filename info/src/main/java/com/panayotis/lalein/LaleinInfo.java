package com.panayotis.lalein;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class LaleinInfo {
    private static final Field registryF = getField(Lalein.class, "registry");

    private final Collection<TranslationInfo> translations = new ArrayList<>();

    @SuppressWarnings("SameParameterValue")
    private static Field getField(Class<?> cls, String field) {
        try {
            Field f = cls.getDeclaredField(field);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new LaleinException("Unable to locate field registry");
        }
    }

    public LaleinInfo(Lalein lalein) {
        Map<String, Translation> registry;
        try {
            //noinspection unchecked
            registry = (Map<String, Translation>) registryF.get(lalein);
        } catch (IllegalAccessException e) {
            throw new LaleinException("Unable to access field " + registryF.getName());
        }
        registry.forEach((key, value) -> translations.add(new TranslationInfo(key, value)));
    }

    public Iterable<TranslationInfo> getTranslations() {
        return translations;
    }

    @Override
    public String toString() {
        return "Lalein[" + translations.size() + "]";
    }
}
