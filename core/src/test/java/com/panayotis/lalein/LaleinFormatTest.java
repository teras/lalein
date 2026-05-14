package com.panayotis.lalein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static com.panayotis.lalein.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic format() semantics: null/missing handlers, plain text, post-processor.
 */
class LaleinFormatTest {

    @Test
    void nullHandler_returnsNull() {
        Lalein l = builder().add("hello", "Hello").build();
        assertNull(l.format(null));
        assertNull(l.format(null, 1, 2, 3));
    }

    @Test
    void missingHandler_isReturnedAsIs() {
        Lalein l = new Lalein(new LinkedHashMap<>());
        assertEquals("ghost", l.format("ghost"));
        assertEquals("Hello, world!", l.format("Hello, world!"));
    }

    @Test
    void missingHandlerWithFormatPlaceholders_runsThroughStringFormat() {
        Lalein l = new Lalein(new LinkedHashMap<>());
        // missing handler is treated as a raw format string
        assertEquals("Number 42", l.format("Number %d", 42));
    }

    @Test
    void simplePlainTranslation() {
        Lalein l = builder().add("hello", "Hello, world!").build();
        assertEquals("Hello, world!", l.format("hello"));
    }

    @Test
    void simpleTranslationWithFormatSpecifier() {
        Lalein l = builder().add("greet", "Hello, %s!").build();
        assertEquals("Hello, Alice!", l.format("greet", "Alice"));
    }

    @Test
    void simpleTranslationExtraArgsIgnored() {
        Lalein l = builder().add("hello", "Hello").build();
        assertEquals("Hello", l.format("hello", "ignored", 1, 2));
    }

    @Test
    void postProcessor_upper() {
        Lalein l = builder().add("hello", "Hello").build();
        l.setPostProcessor(String::toUpperCase);
        assertEquals("HELLO", l.format("hello"));
    }

    @Test
    void postProcessor_returnsNull_propagatesNull() {
        Lalein l = builder().add("hello", "Hello").build();
        l.setPostProcessor(s -> null);
        assertNull(l.format("hello"));
    }

    @Test
    void postProcessor_canRewriteFormatString() {
        Lalein l = builder().add("greet", "Hello %s").build();
        l.setPostProcessor(s -> s + "!!!");
        assertEquals("Hello Bob!!!", l.format("greet", "Bob"));
    }

    @Test
    void setPluralResolverNull_resetsToDefault() {
        Lalein l = builder().add("hello", "Hello").build();
        l.setPluralResolver(null);
        // no exception; the default resolver is reinstated
        assertEquals("Hello", l.format("hello"));
    }

    @Test
    void format_withParameterThatIsAbsent_throws() {
        // Translation has a parameter map (so the resolve loop is entered), but
        // the format references a name not present in the map.
        Lalein l = builder().add("bad", "I want %{missing}", params(
                "other", param(1, null, "o", null, null, null, "r")
        )).build();
        assertThrows(LaleinException.class, () -> l.format("bad", 1));
    }

    @Test
    void format_paramsNull_andFormatHasTag_skipsResolverAndHitsStringFormat() {
        // Quirk: when a translation has no parameter map at all, %{...} placeholders
        // are passed straight to String.format, which doesn't understand the syntax.
        // This documents the current behaviour — the core does NOT validate %{} unless
        // the translation declares a parameter map.
        Lalein l = builder().add("bad", "I want %{missing}").build();
        assertThrows(java.util.UnknownFormatConversionException.class,
                () -> l.format("bad", 1));
    }
}
