package com.portfolio.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringListJsonConverterTest {

    private final StringListJsonConverter converter = new StringListJsonConverter();

    @Test
    void roundTripsListThroughStringAndBack() {
        List<String> original = List.of("java", "spring", "postgres");

        String json = converter.convertToDatabaseColumn(original);
        assertEquals("[\"java\",\"spring\",\"postgres\"]", json);

        List<String> back = converter.convertToEntityAttribute(json);
        assertEquals(original, back);
    }

    @Test
    void roundTripsEmptyList() {
        List<String> original = List.of();

        String json = converter.convertToDatabaseColumn(original);
        assertEquals("[]", json);
        assertEquals(original, converter.convertToEntityAttribute(json));
    }

    @Test
    void nullAndBlankMapToNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute("   "));
    }
}
