package com.panayotis.lalein;

import java.math.BigDecimal;
import java.util.Locale;

import static com.panayotis.lalein.PluralType.*;

public class PluralResolvers {
    /** Tolerance band around integer values — absorbs accumulated floating-point error
     *  so that values like {@code 99.9999999} are treated as {@code 100}. The extra
     *  {@code 1e-12} slack absorbs IEEE 754 representation noise at the exact band edge
     *  (e.g. {@code 0.999999d} is stored as {@code 0.9999989999...} which would otherwise
     *  fall outside a strict {@code 1e-6} comparison). */
    static final double EPSILON = 0.000001000001d;

    private static final String TABLE =
            "A/ak/bho/guw/ln/mg/nso/pa/ti/wa/" +
            "B/am/as/bn/gu/hi/kn/pcm/fa/zu/" +
            "C/ff/hy/kab/" +
            "D/da/" +
            "E/fr/pt/" +
            "F/es/it/ca/" +
            "G/ru/uk/be/" +
            "[/sr/hr/bs/sh/" +
            "\\/lag/" +
            "]/si/" +
            "H/pl/" +
            "I/cs/sk/" +
            "J/lt/" +
            "K/lv/prg/" +
            "L/sl/" +
            "^/dsb/hsb/" +
            "M/ar/ars/" +
            "N/he/" +
            "O/cy/" +
            "P/ga/" +
            "Q/gd/" +
            "R/gv/" +
            "S/ro/mo/" +
            "T/mt/" +
            "U/is/mk/" +
            "V/fil/ceb/tl/" +
            "W/shi/" +
            "X/tzm/" +
            "Y/kw/" +
            "Z/br/";

    private static final PluralResolver NONE = n -> null;

    public static PluralResolver usingCurrentLocale() {
        return usingLocale(Locale.getDefault());
    }

    public static PluralResolver usingLocale(Locale locale) {
        return usingLanguage(locale.getLanguage());
    }

    public static PluralResolver usingLanguage(String language) {
        int idx = TABLE.indexOf("/" + language + "/");
        if (idx < 0) return NONE;
        // Rule codes are 'A'..'^' (one char per rule). Language codes are
        // lowercase letters separated by '/'.
        while (TABLE.charAt(idx) < 'A' || TABLE.charAt(idx) > '^') idx--;
        int rule = TABLE.charAt(idx) - 'A';
        return n -> resolve(rule, n);
    }

    /** Bitmask of rules whose CLDR definition is integer-only: any fractional
     *  input on these rules immediately returns OTHER. Bits set for rules
     *  6, 7, 12, 14..16, 19, 23..25. */
    private static final int INT_ONLY_RULES =
            (1<<6)|(1<<7)|(1<<12)|(1<<14)|(1<<15)|(1<<16)|(1<<19)|(1<<23)|(1<<24)|(1<<25);

    private static PluralType resolve(int rule, Number num) {
        double d = num.doubleValue();
        double absD = d < 0 ? -d : d;
        long rounded = Math.round(d);
        boolean isInt = Math.abs(d - rounded) <= EPSILON;
        long n;     // CLDR operand i (integer part, absolute) — equals source n for integers
        int v = 0;  // CLDR operand v (visible fraction digits)
        long f = 0; // CLDR operand f (visible fraction digits as integer)
        if (isInt) {
            n = rounded < 0 ? -rounded : rounded;
        } else {
            if (((INT_ONLY_RULES >>> rule) & 1) != 0) return null;
            n = (long) absD;
            BigDecimal bd = new BigDecimal(Double.toString(absD)).stripTrailingZeros();
            v = Math.max(0, bd.scale());
            if (v > 0) f = bd.unscaledValue().longValue() % (long) Math.pow(10, v);
        }
        long m10 = n % 10, m100 = n % 100;
        long fm10 = f % 10, fm100 = f % 100;
        // "Visible" mod-10/100: i-digits for integers, f-digits for decimals.
        long umod10 = v == 0 ? m10 : fm10;
        long umod100 = v == 0 ? m100 : fm100;
        boolean millionMany = isInt && n > 0 && n % 1000000 == 0;
        switch (rule) {
            case 0:  return isInt && (n == 0 || n == 1) ? ONE : null;
            case 1:  return absD <= 1 + EPSILON ? ONE : null;
            case 2:  return absD < 2 - EPSILON ? ONE : null;
            case 3:  return absD > EPSILON && absD < 2 - EPSILON ? ONE : null;
            case 4:  return millionMany ? MANY : (absD < 2 - EPSILON ? ONE : null);
            case 5:  return millionMany ? MANY : (isInt && n == 1 ? ONE : null);
            case 6: // russian, ukrainian, belarusian
                if (m10 == 1 && m100 != 11) return ONE;
                if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return FEW;
                return MANY;
            case 26: // serbian, croatian, bosnian, serbo-croatian (no "many"; decimal branches)
                if (umod10 == 1 && umod100 != 11) return ONE;
                return (umod10 >= 2 && umod10 <= 4 && (umod100 < 12 || umod100 > 14)) ? FEW : null;
            case 7: // polish
                if (n == 1) return ONE;
                if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return FEW;
                return MANY;
            case 8: // czech, slovak
                if (v != 0) return MANY;
                if (n == 1) return ONE;
                return n >= 2 && n <= 4 ? FEW : null;
            case 9: // lithuanian
                if (f != 0) return MANY;
                if (m100 >= 11 && m100 <= 19) return null;
                if (m10 == 1) return ONE;
                return m10 >= 2 ? FEW : null;
            case 10: // latvian, prussian
                if (isInt && (m10 == 0 || (m100 >= 11 && m100 <= 19))) return ZERO;
                if (v == 2 && fm100 >= 11 && fm100 <= 19) return ZERO;
                if (isInt && m10 == 1 && m100 != 11) return ONE;
                if (v == 2 && fm10 == 1 && fm100 != 11) return ONE;
                return (v != 0 && v != 2 && fm10 == 1) ? ONE : null;
            case 11: // slovenian (all decimals → few per CLDR)
                if (v != 0) return FEW;
                if (m100 == 1) return ONE;
                if (m100 == 2) return TWO;
                return (m100 == 3 || m100 == 4) ? FEW : null;
            case 12: // arabic, najdi arabic
                if (n == 0) return ZERO;
                if (n == 1) return ONE;
                if (n == 2) return TWO;
                if (m100 >= 3 && m100 <= 10) return FEW;
                return (m100 >= 11 && m100 <= 99) ? MANY : null;
            case 13: // hebrew (CLDR v42+: only one/two/other)
                if (!isInt) return n == 0 ? ONE : null;
                if (n == 1) return ONE;
                return n == 2 ? TWO : null;
            case 14: // welsh
                if (n == 0) return ZERO;
                if (n == 1) return ONE;
                if (n == 2) return TWO;
                if (n == 3) return FEW;
                return n == 6 ? MANY : null;
            case 15: // irish
                if (n == 1) return ONE;
                if (n == 2) return TWO;
                if (n >= 3 && n <= 6) return FEW;
                return (n >= 7 && n <= 10) ? MANY : null;
            case 16: // scottish gaelic
                if (n == 1 || n == 11) return ONE;
                if (n == 2 || n == 12) return TWO;
                return ((n >= 3 && n <= 10) || (n >= 13 && n <= 19)) ? FEW : null;
            case 17: // manx
                if (v != 0) return MANY;
                if (m10 == 1) return ONE;
                if (m10 == 2) return TWO;
                return m100 % 20 == 0 ? FEW : null;
            case 18: // romanian, moldovan
                if (v != 0) return FEW;
                if (n == 1) return ONE;
                return (n == 0 || (m100 >= 1 && m100 <= 19)) ? FEW : null;
            case 19: // maltese
                if (n == 1) return ONE;
                if (n == 2) return TWO;
                if (n == 0 || (m100 >= 3 && m100 <= 10)) return FEW;
                return (m100 >= 11 && m100 <= 19) ? MANY : null;
            case 20: // icelandic, macedonian (t/f decimal extension)
                return (umod10 == 1 && umod100 != 11) ? ONE : null;
            case 21: // filipino, cebuano, tagalog
                return (umod10 != 4 && umod10 != 6 && umod10 != 9) ? ONE : null;
            case 22: // tachelhit
                if (n == 0 || (isInt && n == 1)) return ONE;
                if (!isInt) return null;
                return (n >= 2 && n <= 10) ? FEW : null;
            case 23: // central atlas tamazight
                return (n == 0 || n == 1 || (n >= 11 && n <= 99)) ? ONE : null;
            case 24: { // cornish
                if (n == 0) return ZERO;
                if (n == 1) return ONE;
                long mod20 = m100 % 20;
                if (mod20 == 3) return FEW;
                if (mod20 == 1) return MANY;
                if (mod20 == 2) return TWO;
                long m100k = n % 100000;
                if (n % 1000 == 0 && ((m100k >= 1000 && m100k <= 20000)
                        || m100k == 40000 || m100k == 60000 || m100k == 80000)) return TWO;
                return (n != 0 && n % 1000000 == 100000) ? TWO : null;
            }
            case 25: { // breton — exclude tens digit 1/7/9 (matches 11/71/91, 12/72/92, 13..19/73..79/93..99)
                long tens = m100 / 10;
                if (tens != 1 && tens != 7 && tens != 9) {
                    if (m10 == 1) return ONE;
                    if (m10 == 2) return TWO;
                    if (m10 == 3 || m10 == 4 || m10 == 9) return FEW;
                }
                return (n != 0 && n % 1000000 == 0) ? MANY : null;
            }
            case 27: // lag (rule D + ZERO category for n=0)
                if (absD <= EPSILON) return ZERO;
                return absD < 2 - EPSILON ? ONE : null;
            case 28: // sinhala — n = 0,1 or i = 0 and f = 1
                if (isInt && (n == 0 || n == 1)) return ONE;
                return (n == 0 && f == 1) ? ONE : null;
            case 29: // lower sorbian, upper sorbian (i or f mod 100 = 1/2/3..4)
                if (umod100 == 1) return ONE;
                if (umod100 == 2) return TWO;
                return (umod100 == 3 || umod100 == 4) ? FEW : null;
        }
        return null;
    }
}
