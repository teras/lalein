package com.panayotis.lalein;

class Parameter {
    final int argumentIndex;    // 1-based index
    final String zero;
    final String one;
    final String two;
    final String few;
    final String many;
    final String other;

     Parameter(int argumentIndex, String zero, String one, String two, String few, String many, String other) {
        if (argumentIndex <= 0)
            throw new IndexOutOfBoundsException("An argument index can not be referenced with zero or negative position");
        this.argumentIndex = argumentIndex;
        this.zero = zero;
        this.one = one;
        this.two = two;
        this.few = few;
        this.many = many;
        this.other = other == null ? "" : other;
    }

    String resolve(PluralResolver pluralResolver, Object[] args) {
        if (args == null || args.length == 0)
            throw new LaleinException("A parameter is required but no parameters are given");
        if (args.length < argumentIndex)
            throw new LaleinException("A parameter with index #" + argumentIndex + " is required but only "
                    + args.length + " parameter" + (args.length == 1 ? " is" : "s are") + " given");
        Object argO = args[argumentIndex - 1];
        if (!(argO instanceof Number))
            throw new LaleinException("An numeric argument was required but given " + (argO == null ? "null" : argO.getClass().getName()) + " instead");
        Number arg = (Number) argO;
        double argDouble = arg.doubleValue();
        // handle natural cases
        if (zero != null && (argDouble >= PluralResolvers.ZERO_LOWER && argDouble <= PluralResolvers.ZERO_UPPER))
            return zero;
        if (one != null && (argDouble >= PluralResolvers.ONE_LOWER && argDouble <= PluralResolvers.ONE_UPPER))
            return one;
        if (two != null && (argDouble >= PluralResolvers.TWO_LOWER && argDouble <= PluralResolvers.TWO_UPPER))
            return two;
        // handle rule-based cases
        PluralType pluralType = pluralResolver.findType(arg);
        if (pluralType == null)
            pluralType = PluralType.OTHER;
        if (zero != null && pluralType == PluralType.ZERO)
            return zero;
        if (one != null && pluralType == PluralType.ONE)
            return one;
        if (two != null && pluralType == PluralType.TWO)
            return two;
        if (few != null && pluralType == PluralType.FEW)
            return few;
        if (many != null && pluralType == PluralType.MANY)
            return many;
        return other;
    }
}
