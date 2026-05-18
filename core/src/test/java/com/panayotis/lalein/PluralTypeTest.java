package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluralTypeTest {

    @Test
    void tagValues() {
        assertEquals("z", PluralType.ZERO.tag);
        assertEquals("o", PluralType.ONE.tag);
        assertEquals("t", PluralType.TWO.tag);
        assertEquals("f", PluralType.FEW.tag);
        assertEquals("m", PluralType.MANY.tag);
        assertEquals("r", PluralType.OTHER.tag);
    }

    @Test
    void isPluralTag_reservedKeys() {
        for (PluralType t : PluralType.values())
            assertTrue(PluralType.isPluralTag(t.tag), t.tag + " should be a plural tag");
    }

    @Test
    void isPluralTag_unknownKey() {
        assertFalse(PluralType.isPluralTag("zoo"));
        assertFalse(PluralType.isPluralTag("female"));
        assertFalse(PluralType.isPluralTag(""));
        assertFalse(PluralType.isPluralTag("Z"));   // case-sensitive
    }
}
