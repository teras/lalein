package com.panayotis.lalein;

public interface TranslationUnit {
    String getHandler();

    String getFormat();

    Iterable<Parameter> getParameters();
}
