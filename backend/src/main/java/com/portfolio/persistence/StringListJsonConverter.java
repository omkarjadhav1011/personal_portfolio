package com.portfolio.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Persists a {@code List<String>} as a JSON-encoded string column.
 *
 * <p>Mirrors the "JSON stored as String" pattern from the original Next.js/Prisma
 * schema. Attach with {@code @Convert(converter = StringListJsonConverter.class)} on:
 * {@code Project.tags}, {@code CommitEntry.description}, {@code CommitEntry.tags},
 * {@code Profile.funFacts} and {@code Profile.stash}.
 *
 * <p>Not auto-applied: object-shaped JSON columns ({@code Profile.socials},
 * {@code Profile.currentRole}) use their own converters.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize List<String> to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to List<String>: " + dbData, e);
        }
    }
}
