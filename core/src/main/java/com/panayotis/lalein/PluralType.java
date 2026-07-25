package com.panayotis.lalein;

public enum PluralType {
    ZERO("z"),
    ONE("o"),
    TWO("t"),
    FEW("f"),
    MANY("m"),
    OTHER("r"); // remaining
    public final String tag;

    PluralType(String tag) {
        this.tag = tag;
    }

    public static boolean isPluralTag(String key) {
        return key.length() == 1 && "zotfmr".indexOf(key.charAt(0)) >= 0;
    }
}
