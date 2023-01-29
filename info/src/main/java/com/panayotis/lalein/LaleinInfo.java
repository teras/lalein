package com.panayotis.lalein;

import java.util.ArrayList;
import java.util.Collection;

public class LaleinInfo {

    private final Collection<TranslationInfo> translations = new ArrayList<>();

    public LaleinInfo(Lalein lalein) {
        lalein.entries().forEach(e -> translations.add(new TranslationInfo(e.getKey(), e.getValue())));
    }

    public Iterable<TranslationInfo> getTranslations() {
        return translations;
    }

    @Override
    public String toString() {
        return "Lalein[" + translations.size() + "]";
    }
}
