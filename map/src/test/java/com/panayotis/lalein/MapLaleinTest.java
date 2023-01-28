package com.panayotis.lalein;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapLaleinTest {

    @Test
    void fromString() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, ?> data = new ObjectMapper().readValue(MapLaleinTest.class.getResourceAsStream("/Localizable.json"), Map.class);
        Lalein lalein = MapLalein.fromMap(data);

        assertEquals("I have peaches.", lalein.format("peaches"));

        assertEquals("I don't have apples.", lalein.format("apples", 0));
        assertEquals("I have an apple.", lalein.format("apples", 1));
        assertEquals("I have two apples.", lalein.format("apples", 2));
        assertEquals("I have 27 apples.", lalein.format("apples", 27));

        assertEquals("I don't have a basket or an orange.", lalein.format("baskets_with_oranges", 0, 0));
        assertEquals("I don't have a basket but I have an orange.", lalein.format("baskets_with_oranges", 0, 1));
        assertEquals("I don't have a basket but I have 10 oranges.", lalein.format("baskets_with_oranges", 0, 10));
        assertEquals("I have a basket without oranges.", lalein.format("baskets_with_oranges", 1, 0));
        assertEquals("I have a basket with one orange.", lalein.format("baskets_with_oranges", 1, 1));
        assertEquals("I have a basket with 8 oranges.", lalein.format("baskets_with_oranges", 1, 8));
        assertEquals("I have 7 baskets without oranges.", lalein.format("baskets_with_oranges", 7, 0));
        assertEquals("I have 7 baskets with one orange.", lalein.format("baskets_with_oranges", 7, 1));
        assertEquals("I have 7 baskets with 9 oranges.", lalein.format("baskets_with_oranges", 7, 9));

        assertEquals("This does not exist", lalein.format("This does not exist"));
    }

}