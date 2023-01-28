package com.panayotis.lalein;

public class ParameterInfo {
    private final TranslationInfo parent;
    private final String handler;

    private final int index;    // 1-based index
    private final String zero;
    private final String one;
    private final String two;
    private final String few;
    private final String many;
    private final String other;

    public ParameterInfo(TranslationInfo parent, String handler, Parameter parameter) {
        this.parent = parent;
        this.handler = handler;
        this.index = parameter.argumentIndex;
        this.zero = parameter.zero;
        this.one = parameter.one;
        this.two = parameter.two;
        this.few = parameter.few;
        this.many = parameter.many;
        this.other = parameter.other;
    }

    public String getHandler() {
        return handler;
    }

    public int getIndex() {
        return index;
    }

    public String getZero() {
        return zero;
    }

    public String getOne() {
        return one;
    }

    public String getTwo() {
        return two;
    }

    public String getFew() {
        return few;
    }

    public String getMany() {
        return many;
    }

    public String getOther() {
        return other;
    }

    public TranslationInfo getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "(" + index + ") " + handler;
    }
}
