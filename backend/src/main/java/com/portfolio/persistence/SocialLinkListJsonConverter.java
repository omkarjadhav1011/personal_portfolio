package com.portfolio.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.portfolio.profile.SocialLink;
import jakarta.persistence.Converter;

import java.util.List;

/** Persists {@code Profile.socials} ({@code List<SocialLink>}) as a JSON string column. */
@Converter
public class SocialLinkListJsonConverter extends JsonAttributeConverter<List<SocialLink>> {

    public SocialLinkListJsonConverter() {
        super(new TypeReference<>() {});
    }
}
