package com.panayotis.lalein;

import java.util.Locale;

import static com.panayotis.lalein.PluralType.ONE;

@SuppressWarnings("unused")
public class PluralResolvers {
    final static double ZERO_LOWER = -0.000001d;
    final static double ZERO_UPPER = 0.000001d;
    final static double ONE_LOWER = 0.999999d;
    final static double ONE_UPPER = 1.000001d;
    final static double TWO_LOWER = 1.999999d;
    final static double TWO_UPPER = 2.000001d;

    // exactly zero -> 1
    private static final String EXACT_ZERO = "/ak/bho/ln/mg/nso/pa/ti/wa/";
    // zero to one -> 1
    private static final String ZERO_TO_ONE = "/am/as/bn/gu/guw/hi/kn/pcm/fa/zu/";
    // zero to almost two -> 1
    private static final String BELOW_TWO = "/hy/fr/ff/kab/";
    // between more than zero and almost two -> 1
    private static final String POSITIVE_BELOW_TWO = "/da/lag/pt/";
    // not supported yet
    private static final String UNSUPPORTED = "/ar/be/bs/br/ceb/tzm/kw/hr/cs/fil/he/is/ga/lv/lt/dsb/mk/mt/gv/ars/pl/prg/ro/ru/gd/sr/si/sk/sl/shi/uk/hsb/cy/";

    public static PluralResolver usingCurrentLocale() {
        return usingLocale(Locale.getDefault());
    }

    public static PluralResolver usingLocale(Locale locale) {
        return resolve(locale.getLanguage(), locale.getDisplayLanguage());
    }

    public static PluralResolver usingLanguage(String language) {
        return resolve(language, language);
    }

    private static PluralResolver resolve(String language, String fullNameLanguage) {
        String key = "/" + language + "/";
        if (EXACT_ZERO.contains(key))
            return n -> n.doubleValue() >= ZERO_LOWER && n.doubleValue() <= ZERO_UPPER ? ONE : null;
        if (ZERO_TO_ONE.contains(key))
            return n -> n.doubleValue() <= ONE_UPPER ? ONE : null;
        if (BELOW_TWO.contains(key))
            return n -> n.doubleValue() < TWO_LOWER ? ONE : null;
        if (POSITIVE_BELOW_TWO.contains(key))
            return n -> n.doubleValue() > ZERO_UPPER && n.doubleValue() < TWO_LOWER ? ONE : null;
        if (UNSUPPORTED.contains(key))
            throw new LaleinException("Language " + fullNameLanguage + " not supported yet. Please provide a manual PluralResolver");
        return n -> null;
    }
}
