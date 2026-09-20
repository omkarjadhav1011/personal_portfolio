package com.portfolio.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup-validation policy: misconfiguration (chain typos, key-without-model, cap ≤ 0) fails
 * startup with a clear message; a merely-missing key is NOT an error (conditional wiring).
 */
class LlmProviderConfigTest {

    @Test
    void parsesChainPreservingOrderAndDedupes() {
        assertEquals(List.of("mistral", "groq", "gemini"),
                LlmProviderConfig.parseChain(" Mistral, groq ,gemini,groq", LlmProviderConfig.KNOWN_PROVIDERS));
    }

    @Test
    void unknownProviderInChainFailsStartupWithItsName() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> LlmProviderConfig.parseChain("groq,gorq", LlmProviderConfig.KNOWN_PROVIDERS));
        assertTrue(thrown.getMessage().contains("'gorq'"), "the typo must be named in the error");
    }

    @Test
    void emptyChainFailsStartup() {
        assertThrows(IllegalStateException.class,
                () -> LlmProviderConfig.parseChain(" , ", LlmProviderConfig.KNOWN_PROVIDERS));
    }

    @Test
    void keyWithoutModelFailsStartupButMissingKeyIsFine() {
        assertThrows(IllegalStateException.class,
                () -> LlmProviderConfig.requireModelWithKey("groq", "sk-key", " "));
        assertDoesNotThrow(() -> LlmProviderConfig.requireModelWithKey("groq", "", ""));
        assertDoesNotThrow(() -> LlmProviderConfig.requireModelWithKey("groq", "sk-key", "gpt-oss-120b"));
    }

    @Test
    void nonPositiveDailyCapFailsStartup() {
        assertThrows(IllegalStateException.class,
                () -> LlmProviderConfig.requirePositiveCap("LLM_GROQ_DAILY_CAP", 0));
        assertThrows(IllegalStateException.class,
                () -> LlmProviderConfig.requirePositiveCap("LLM_GROQ_DAILY_CAP", -5));
        assertDoesNotThrow(() -> LlmProviderConfig.requirePositiveCap("LLM_GROQ_DAILY_CAP", 950));
    }
}
