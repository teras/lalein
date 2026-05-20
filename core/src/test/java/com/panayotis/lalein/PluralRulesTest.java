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
        // "lag" — 0 < n < 2 -> ONE; exactly 0 is excluded.
        Lalein l = l("n", onlyOneOther());
        l.setPluralResolver(PluralResolvers.usingLanguage("lag"));
        assertEquals("OTHER", l.format("n", 0));  // rule null, no o-defined-for-natural, falls to OTHER
        assertEquals("ONE", l.format("n", 0.5));
        assertEquals("ONE", l.format("n", 1));
        assertEquals("ONE", l.format("n", 1.5));
        assertEquals("OTHER", l.format("n", 2));
    }

    @Test
    void aboveZeroToAlmostTwo_withZeroDefined_zeroWins() {
        Lalein l = l("n", zorN());
        l.setPluralResolver(PluralResolvers.usingLanguage("lag"));
        assertEquals("ZERO", l.format("n", 0));  // natural ZERO band hits before rule
    }

    @Test
    void romanceOneMany_spanish_largeMultiplesOfMillion() {
        // CLDR "many" category fires only for non-zero integer multiples of 1_000_000.
        PluralResolver r = PluralResolvers.usingLanguage("es");
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.MANY, r.findType(1_000_000));
        assertEquals(PluralType.MANY, r.findType(2_000_000));
        assertNull(r.findType(0));            // CLDR: other
        assertNull(r.findType(2));
        assertNull(r.findType(1_000_001));
    }

    @Test
    void romanceOneMany_portuguese_followsDefaultCldr() {
        // pt now follows default CLDR (i = 0..1 → one), same band as fr/hy.
        // many fires for non-zero integer multiples of 1_000_000 (compact form).
        PluralResolver r = PluralResolvers.usingLanguage("pt");
        assertEquals(PluralType.ONE,  r.findType(0));
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.ONE,  r.findType(0.5));   // i=0 → one
        assertEquals(PluralType.MANY, r.findType(1_000_000));
        assertNull(r.findType(2));
    }

    @Test
    void frenchHy_addsManyToBelowTwoBand() {
        // fr / hy: same band as before (n < 2 -> one), plus many for very large round counts.
        PluralResolver r = PluralResolvers.usingLanguage("fr");
        assertEquals(PluralType.ONE,  r.findType(0));
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.ONE,  r.findType(0.5));
        assertEquals(PluralType.ONE,  r.findType(1.5));
        assertEquals(PluralType.MANY, r.findType(1_000_000));
        assertNull(r.findType(2));
        assertNull(r.findType(100));
    }

    // The following tests probe the CLDR rule directly. We bypass Lalein's
    // Parameter.resolve because it short-circuits for the exact values 0/1/2
    // when the corresponding slot is non-null — that hides the resolver's
    // verdict, which is precisely what we want to verify here.

    @Test
    void slavic3_russian_distinguishesOneFewMany() {
        PluralResolver r = PluralResolvers.usingLanguage("ru");
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.ONE,  r.findType(21));   // 21 -> one
        assertEquals(PluralType.ONE,  r.findType(101));
        assertEquals(PluralType.FEW,  r.findType(2));
        assertEquals(PluralType.FEW,  r.findType(23));
        assertEquals(PluralType.MANY, r.findType(5));
        assertEquals(PluralType.MANY, r.findType(11));   // 11 excluded from one
        assertEquals(PluralType.MANY, r.findType(12));   // 12 excluded from few
        assertEquals(PluralType.MANY, r.findType(0));
    }

    @Test
    void polish_distinguishesOneFromOtherSingulars() {
        PluralResolver r = PluralResolvers.usingLanguage("pl");
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.MANY, r.findType(21));   // differs from ru
        assertEquals(PluralType.FEW,  r.findType(2));
        assertEquals(PluralType.FEW,  r.findType(23));
        assertEquals(PluralType.MANY, r.findType(5));
        assertEquals(PluralType.MANY, r.findType(0));
    }

    @Test
    void czechSlovak_distinguishesOneFewOther() {
        PluralResolver r = PluralResolvers.usingLanguage("cs");
        assertEquals(PluralType.ONE, r.findType(1));
        assertEquals(PluralType.FEW, r.findType(2));
        assertEquals(PluralType.FEW, r.findType(4));
        assertNull(r.findType(5));                       // -> other via fall-through
        assertNull(r.findType(22));                      // differs from pl (which is few)
        assertNull(r.findType(0));
    }

    @Test
    void arabic_allSixCategories() {
        PluralResolver r = PluralResolvers.usingLanguage("ar");
        assertEquals(PluralType.ZERO, r.findType(0));
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.TWO,  r.findType(2));
        assertEquals(PluralType.FEW,  r.findType(3));
        assertEquals(PluralType.FEW,  r.findType(10));
        assertEquals(PluralType.MANY, r.findType(11));
        assertEquals(PluralType.MANY, r.findType(99));
        assertNull(r.findType(100));
        assertNull(r.findType(101));
    }

    @Test
    void welsh_exactMatches() {
        PluralResolver r = PluralResolvers.usingLanguage("cy");
        assertEquals(PluralType.ZERO, r.findType(0));
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.TWO,  r.findType(2));
        assertEquals(PluralType.FEW,  r.findType(3));
        assertEquals(PluralType.MANY, r.findType(6));
        assertNull(r.findType(4));
        assertNull(r.findType(5));
    }

    @Test
    void hebrew_oneTwoOther_modernCldr() {
        // CLDR v42+: Hebrew has only one/two/other (the historical "many"
        // category for multiples of 10 was removed).
        PluralResolver r = PluralResolvers.usingLanguage("he");
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.TWO,  r.findType(2));
        assertNull(r.findType(10));
        assertNull(r.findType(30));
        assertNull(r.findType(0));
        assertNull(r.findType(5));
    }

    @Test
    void slovenian_modHundred() {
        PluralResolver r = PluralResolvers.usingLanguage("sl");
        assertEquals(PluralType.ONE, r.findType(1));
        assertEquals(PluralType.ONE, r.findType(101));   // 101 % 100 = 1
        assertEquals(PluralType.TWO, r.findType(2));
        assertEquals(PluralType.TWO, r.findType(102));
        assertEquals(PluralType.FEW, r.findType(3));
        assertEquals(PluralType.FEW, r.findType(4));
        assertNull(r.findType(5));
        assertNull(r.findType(100));
    }

    @Test
    void latvian_zeroBucket() {
        PluralResolver r = PluralResolvers.usingLanguage("lv");
        assertEquals(PluralType.ZERO, r.findType(0));
        assertEquals(PluralType.ZERO, r.findType(10));
        assertEquals(PluralType.ZERO, r.findType(11));
        assertEquals(PluralType.ZERO, r.findType(19));
        assertEquals(PluralType.ONE,  r.findType(1));
        assertEquals(PluralType.ONE,  r.findType(21));
        assertNull(r.findType(2));
        assertNull(r.findType(22));
    }

    @Test
    void unsupportedLanguage_noLongerThrows() {
        // Previously these threw LaleinException; now they return functional resolvers.
        assertNotNull(PluralResolvers.usingLanguage("ru"));
        assertNotNull(PluralResolvers.usingLanguage("ar"));
        assertNotNull(PluralResolvers.usingLanguage("pl"));
        assertNotNull(PluralResolvers.usingLanguage("cy"));
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
