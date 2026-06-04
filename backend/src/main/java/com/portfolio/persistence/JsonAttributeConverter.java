package com.portfolio.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

/**
 * Base for JPA converters that persist an object graph as a JSON-encoded string column.
 * Subclasses pass a {@link TypeReference} for their concrete type. {@code null}/blank
 * map to {@code null} in both directions. Unknown JSON properties are ignored so the
 * schema can evolve without breaking reads.
 */
public abstract class JsonAttributeConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TypeReference<T> typeRef;

    protected JsonAttributeConverter(TypeReference<T> typeRef) {
        this.typeRef = typeRef;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize value to JSON", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, typeRef);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON: " + dbData, e);
        }
    }
}
