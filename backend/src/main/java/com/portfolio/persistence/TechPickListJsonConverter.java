package com.portfolio.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.portfolio.profile.TechPick;
import jakarta.persistence.Converter;

import java.util.List;

/** Persists {@code Profile.techPicks} ({@code List<TechPick>}) as a JSON string column. */
@Converter
public class TechPickListJsonConverter extends JsonAttributeConverter<List<TechPick>> {

    public TechPickListJsonConverter() {
        super(new TypeReference<>() {});
    }
}
