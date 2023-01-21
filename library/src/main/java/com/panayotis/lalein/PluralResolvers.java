package com.panayotis.lalein;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.panayotis.lalein.PluralType.ONE;

@SuppressWarnings("unused")
public class PluralResolvers {
    final static double ZERO_LOWER = -0.000001d;
    final static double ZERO_UPPER = 0.000001d;
    final static double ONE_LOWER = 0.999999d;
    final static double ONE_UPPER = 1.000001d;
    final static double TWO_LOWER = 1.999999d;
    final static double TWO_UPPER = 2.000001d;

    private static final Map<String, PluralResolver> specialResolvers = new HashMap<>();

    static {
        // exactly zero -> 1
        specialResolvers.put("/ak/bho/ln/mg/nso/pa/ti/wa/", n -> n.doubleValue() >= ZERO_LOWER && n.doubleValue() <= ZERO_UPPER ? ONE : null);
        // zero to one -> 1
        specialResolvers.put("/am/as/bn/gu/guw/hi/kn/pcm/fa/zu/", n -> n.doubleValue() <= ONE_UPPER ? ONE : null);
        // zero to almost two -> 1
        specialResolvers.put("/hy/fr/ff/kab/", n -> n.doubleValue() < TWO_LOWER ? ONE : null);
        // between more than zero and almost 2 -> 1
        specialResolvers.put("/da/lag/pt/", n -> n.doubleValue() > ZERO_UPPER && n.doubleValue() < TWO_LOWER ? ONE : null);
        // not supported yet
        specialResolvers.put("/ar/be/bs/br/ceb/tzm/kw/hr/cs/fil/he/is/ga/lv/lt/dsb/mk/mt/gv/ars/pl/prg/ro/ru/gd/sr/si/sk/sl/shi/uk/hsb/cy/", null);
    }


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
        language = "/" + language + "/";
        for (String key : specialResolvers.keySet())
            if (key.contains(language)) {
                PluralResolver resolver = specialResolvers.get(key);
                if (resolver == null)
                    throw new IllegalArgumentException("Language " + fullNameLanguage + " not supported yet. Please provide a manual PluralResolver");
                else return resolver;
            }
        return n -> null;
    }
}
