package com.panayotis.lalein;

import java.util.Map;
import java.util.Objects;

class Parameter {
    final int argumentIndex;    // 1-based index
    final String zero;
    final String one;
    final String two;
    final String few;
    final String many;
    final String other;
    /** Non-CLDR keys for select-mode (gender, formality, etc.). May be null. */
    final Map<String, String> custom;

    Parameter(int argumentIndex, String zero, String one, String two, String few, String many, String other) {
        this(argumentIndex, zero, one, two, few, many, other, null);
    }

    Parameter(int argumentIndex, String zero, String one, String two, String few, String many, String other,
              Map<String, String> custom) {
        if (argumentIndex <= 0)
            throw new IndexOutOfBoundsException("An argument index can not be referenced with zero or negative position");
        this.argumentIndex = argumentIndex;
        this.zero = zero;
        this.one = one;
        this.two = two;
        this.few = few;
        this.many = many;
        this.other = other == null ? "" : other;
        this.custom = (custom == null || custom.isEmpty()) ? null : custom;
    }

    String resolve(PluralResolver pluralResolver, String handler, String name, Object[] args) {
        String where = " (parameter '" + name + "' of '" + handler + "')";
        if (args == null || args.length == 0)
            throw new LaleinException("A numeric argument is required but none was given" + where);
        if (args.length < argumentIndex)
            throw new LaleinException("A numeric argument at position #" + argumentIndex + " is required but only "
                    + args.length + " argument" + (args.length == 1 ? " was" : "s were") + " given" + where);
        Object argO = args[argumentIndex - 1];
        if (argO instanceof Number) {
            Number arg = (Number) argO;
            double argDouble = arg.doubleValue();
            if (zero != null && (argDouble >= PluralResolvers.ZERO_LOWER && argDouble <= PluralResolvers.ZERO_UPPER))
                return zero;
            if (one != null && (argDouble >= PluralResolvers.ONE_LOWER && argDouble <= PluralResolvers.ONE_UPPER))
                return one;
            if (two != null && (argDouble >= PluralResolvers.TWO_LOWER && argDouble <= PluralResolvers.TWO_UPPER))
                return two;
            PluralType pluralType = pluralResolver.findType(arg);
            if (pluralType == null)
                pluralType = PluralType.OTHER;
            if (zero != null && pluralType == PluralType.ZERO) return zero;
            if (one != null && pluralType == PluralType.ONE) return one;
            if (two != null && pluralType == PluralType.TWO) return two;
            if (few != null && pluralType == PluralType.FEW) return few;
            if (many != null && pluralType == PluralType.MANY) return many;
            return other;
        }
        if (custom == null)
            throw new LaleinException("A numeric argument at position #" + argumentIndex + " is required but got "
                    + (argO == null ? "null" : argO.getClass().getName()) + where);
        String form = argO == null ? null : custom.get(argO.toString());
        return form == null ? other : form;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Parameter)) return false;
        Parameter parameter = (Parameter) o;
        return argumentIndex == parameter.argumentIndex
                && Objects.equals(zero, parameter.zero)
                && Objects.equals(one, parameter.one)
                && Objects.equals(two, parameter.two)
                && Objects.equals(few, parameter.few)
                && Objects.equals(many, parameter.many)
                && Objects.equals(other, parameter.other)
                && Objects.equals(custom, parameter.custom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(argumentIndex, zero, one, two, few, many, other, custom);
    }
}
