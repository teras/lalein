package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies PluralResolvers behaviour against the curated language groups defined
 * in the static initializer. Each group has a specific rule:
 *   - "ak/bho/ln/mg/nso/pa/ti/wa"   -> n == 0   -> ONE
 *   - "am/as/bn/gu/guw/hi/kn/pcm/fa/zu" -> n <= 1 -> ONE
 *   - "hy/fr/ff/kab"                -> n < 2    -> ONE
 *   - "da/lag/pt"                   -> 0 < n < 2 -> ONE
 *   - "ar/be/bs/br/.../cy"          -> unsupported -> throws
 */
class PluralRulesTest {

    private Lalein l(String key, Parameter p) {
        Lalein x = builder().add("n", "%{p}", params("p", p)).build();
        return x;
    }

    private static Parameter zorN() {
        return param(1, "ZERO", "ONE", null, null, null, "OTHER");
    }

    private static Parameter onlyOneOther() {
        return param(1, null, "ONE", null, null, null, "OTHER");
    }

    @Test
    void englishLikeUnmapped_returnsNullResolver_naturalDetectionOnly() {
        Lalein l = l("n", zorN());
        l.setPluralResolver(PluralResolvers.usingLanguage("en"));
        assertEquals("ZERO", l.format("n", 0));
        assertEquals("ONE", l.format("n", 1));
        assertEquals("OTHER", l.format("n", 2));
        assertEquals("OTHER", l.format("n", 5));
        assertEquals("OTHER", l.format("n", 0.5));
    }

    @Test
    void zeroMapsToOne_group() {
        // "pa" (Punjabi) — n == 0 -> ONE
        // With both z and o defined: natural ZERO takes priority for 0.
        Lalein lZorO = l("n", zorN());
        lZorO.setPluralResolver(PluralResolvers.usingLanguage("pa"));
        assertEquals("ZERO", lZorO.format("n", 0));
        assertEquals("ONE", lZorO.format("n", 1));
        assertEquals("OTHER", lZorO.format("n", 2));
        // Without z defined: 0 falls back to rule which says ONE
        Lalein lOnlyO = l("n", onlyOneOther());
        lOnlyO.setPluralResolver(PluralResolvers.usingLanguage("pa"));
        assertEquals("ONE", lOnlyO.format("n", 0));
        assertEquals("ONE", lOnlyO.format("n", 1));
        assertEquals("OTHER", lOnlyO.format("n", 3));
    }

    @Test
    void zeroThroughOne_group() {
        // "hi" — n <= 1 -> ONE; 0, 0.5, 1 all map to ONE.
        Lalein l = l("n", onlyOneOther());
        l.setPluralResolver(PluralResolvers.usingLanguage("hi"));
        assertEquals("ONE", l.format("n", 0));
        assertEquals("ONE", l.format("n", 0.5));
        assertEquals("ONE", l.format("n", 1));
        assertEquals("OTHER", l.format("n", 2));
        assertEquals("OTHER", l.format("n", 1.5));  // 1.5 > 1.000001 so not natural ONE; 1.5 > 1 so rule null
    }

    @Test
    void zeroToAlmostTwo_group() {
        // "fr" — n < 2 -> ONE
        Lalein l = l("n", onlyOneOther());
        l.setPluralResolver(PluralResolvers.usingLanguage("fr"));
        assertEquals("ONE", l.format("n", 0));
        assertEquals("ONE", l.format("n", 0.5));
        assertEquals("ONE", l.format("n", 1));
        assertEquals("ONE", l.format("n", 1.5));
        assertEquals("OTHER", l.format("n", 2));
        assertEquals("OTHER", l.format("n", 2.5));
        assertEquals("OTHER", l.format("n", 1000000));
    }

    @Test
    void aboveZeroToAlmostTwo_group() {
        // "pt" — 0 < n < 2 -> ONE; exactly 0 is excluded.
        Lalein l = l("n", onlyOneOther());
        l.setPluralResolver(PluralResolvers.usingLanguage("pt"));
        assertEquals("OTHER", l.format("n", 0));  // rule null, no o-defined-for-natural, falls to OTHER
        assertEquals("ONE", l.format("n", 0.5));
        assertEquals("ONE", l.format("n", 1));
        assertEquals("ONE", l.format("n", 1.5));
        assertEquals("OTHER", l.format("n", 2));
    }

    @Test
    void aboveZeroToAlmostTwo_withZeroDefined_zeroWins() {
        Lalein l = l("n", zorN());
        l.setPluralResolver(PluralResolvers.usingLanguage("pt"));
        assertEquals("ZERO", l.format("n", 0));  // natural ZERO band hits before rule
    }

    @Test
    void unsupportedLanguage_throws() {
        // "ru" is in the not-supported-yet list
        Lalein l = l("n", onlyOneOther());
        assertThrows(LaleinException.class,
                () -> l.setPluralResolver(PluralResolvers.usingLanguage("ru")));
    }

    @Test
    void unsupportedLanguage_arabic_throws() {
        assertThrows(LaleinException.class,
                () -> PluralResolvers.usingLanguage("ar"));
    }

    @Test
    void unsupportedLanguage_polish_throws() {
        assertThrows(LaleinException.class,
                () -> PluralResolvers.usingLanguage("pl"));
    }

    @Test
    void unsupportedLanguage_welsh_throws() {
        assertThrows(LaleinException.class,
                () -> PluralResolvers.usingLanguage("cy"));
    }

    @Test
    void usingLocale_delegatesToLanguage() {
        Lalein l = l("n", onlyOneOther());
        l.setPluralResolver(PluralResolvers.usingLocale(Locale.FRENCH));
        assertEquals("ONE", l.format("n", 1.5));
        assertEquals("OTHER", l.format("n", 2.5));
    }

    @Test
    void usingCurrentLocale_doesNotThrow() {
        // Whatever the current locale is, this should produce a usable resolver
        // (it will throw only if current locale is in the unsupported list — unlikely on CI).
        // Just guard against accidental nulls.
        assertNotNull(PluralResolvers.usingCurrentLocale());
    }

    @Test
    void unmappedLanguage_returnsAllNullResolver_doesNotThrow() {
        // "xx" doesn't match any group — should return a resolver that always returns null
        PluralResolver r = PluralResolvers.usingLanguage("xx");
        assertNotNull(r);
        assertNull(r.findType(5));
        assertNull(r.findType(0));
    }
}
