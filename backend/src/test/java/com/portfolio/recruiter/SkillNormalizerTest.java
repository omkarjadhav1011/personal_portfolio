package com.portfolio.recruiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SkillNormalizerTest {

    @Test
    void canonicalizesSpellingVariants() {
        assertEquals("react", SkillNormalizer.normalize("React.js"));
        assertEquals("react", SkillNormalizer.normalize("ReactJS"));
        assertEquals("postgresql", SkillNormalizer.normalize("Postgres"));
        assertEquals("postgresql", SkillNormalizer.normalize("PostgreSQL"));
        assertEquals("springboot", SkillNormalizer.normalize("Spring Boot"));
        assertEquals("springboot", SkillNormalizer.normalize("spring-boot"));
        assertEquals("kubernetes", SkillNormalizer.normalize("K8s"));
        assertEquals("javascript", SkillNormalizer.normalize("JS"));
        assertEquals("node", SkillNormalizer.normalize("Node.js"));
        assertEquals("go", SkillNormalizer.normalize("Golang"));
    }

    @Test
    void dropsTrailingVersionTokensOnMultiWordNamesOnly() {
        assertEquals("java", SkillNormalizer.normalize("Java 21"));
        assertEquals("springboot", SkillNormalizer.normalize("Spring Boot 3.3"));
        assertEquals("vue", SkillNormalizer.normalize("Vue 3"));
        // Single tokens that end in digits are product names, not versions.
        assertEquals("s3", SkillNormalizer.normalize("S3"));
        assertEquals("ec2", SkillNormalizer.normalize("EC2"));
    }

    @Test
    void keepsCVariantsDistinct() {
        assertNotEquals(SkillNormalizer.normalize("C"), SkillNormalizer.normalize("C++"));
        assertNotEquals(SkillNormalizer.normalize("C"), SkillNormalizer.normalize("C#"));
        assertNotEquals(SkillNormalizer.normalize("C++"), SkillNormalizer.normalize("C#"));
    }

    @Test
    void isDeterministic() {
        for (int i = 0; i < 5; i++) {
            assertEquals("springboot", SkillNormalizer.normalize("  Spring Boot 3.x "));
        }
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", SkillNormalizer.normalize(null));
        assertEquals("", SkillNormalizer.normalize("   "));
    }
}
