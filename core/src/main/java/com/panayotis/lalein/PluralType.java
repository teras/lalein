package com.panayotis.lalein;

import java.util.HashSet;
import java.util.Set;

public enum PluralType {
    ZERO("z"),
    ONE("o"),
    TWO("t"),
    FEW("f"),
    MANY("m"),
    OTHER("r"); // remaining
    public final String tag;

    private static final Set<String> ALL_TAGS;
    static {
        ALL_TAGS = new HashSet<>();
        for (PluralType t : values()) ALL_TAGS.add(t.tag);
    }

    PluralType(String tag) {
        this.tag = tag;
    }

    public static boolean isPluralTag(String key) {
        return ALL_TAGS.contains(key);
    }
}
