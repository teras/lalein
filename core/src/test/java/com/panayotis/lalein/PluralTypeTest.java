package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

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
    void findInvalidKey_allValid_returnsNull() {
        assertNull(PluralType.findInvalidKey(Arrays.asList("z", "o", "t", "f", "m", "r")));
        assertNull(PluralType.findInvalidKey(Collections.emptyList()));
        assertNull(PluralType.findInvalidKey(Collections.singletonList("o")));
    }

    @Test
    void findInvalidKey_returnsFirstInvalid() {
        assertEquals("zoo", PluralType.findInvalidKey(Arrays.asList("o", "zoo", "r")));
    }

    @Test
    void findInvalidKey_caseSensitive() {
        assertEquals("Z", PluralType.findInvalidKey(Collections.singletonList("Z")));
    }

    @Test
    void findInvalidKey_emptyStringIsInvalid() {
        assertEquals("", PluralType.findInvalidKey(Collections.singletonList("")));
    }
}
