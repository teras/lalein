package com.panayotis.lalein;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public enum PluralType {
    ZERO("z"),
    ONE("o"),
    TWO("t"),
    FEW("f"),
    MANY("m"),
    OTHER("r"); // remaining
    public final String tag;

    private static final Collection<String> ALL_TAGS = Arrays.stream(PluralType.values()).map(it -> it.tag).collect(Collectors.toSet());

    PluralType(String tag) {
        this.tag = tag;
    }

    public static String findInvalidKey(Collection<String> keysToCheck) {
        for (String key : keysToCheck)
            if (!ALL_TAGS.contains(key))
                return key;
        return null;
    }
}
