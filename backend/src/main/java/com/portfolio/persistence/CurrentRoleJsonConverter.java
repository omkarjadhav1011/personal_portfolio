package com.portfolio.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.portfolio.profile.CurrentRole;
import jakarta.persistence.Converter;

/** Persists {@code Profile.currentRole} ({@code CurrentRole}) as a JSON string column. */
@Converter
public class CurrentRoleJsonConverter extends JsonAttributeConverter<CurrentRole> {

    public CurrentRoleJsonConverter() {
        super(new TypeReference<>() {});
    }
}
