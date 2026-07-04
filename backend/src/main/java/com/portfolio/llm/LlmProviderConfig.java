package com.portfolio.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Builds the failover chain from env ({@code LLM_PROVIDER_CHAIN} + per-provider keys/models)
 * and owns the conditional-wiring policy: a provider whose key is missing is <b>skipped with a
 * loud boot summary</b> (the app must serve the portfolio with zero AI keys), while genuine
 * misconfiguration — an unknown id in the chain, a key with a blank model, a non-positive daily
 * cap — <b>fails startup</b> with a clear message, JwtService-style, because silently skipping a
 * typo would be a trap.
 */
@Configuration
public class LlmProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderConfig.class);

    static final Set<String> KNOWN_PROVIDERS = Set.of("groq", "cerebras", "mistral", "gemini", "openrouter");

    @Bean
    public LlmRouter llmRouter(
            @Value("${LLM_PROVIDER_CHAIN:groq,cerebras,mistral,gemini,openrouter}") String chainSpec,
            @Value("${GROQ_API_KEY:}") String groqKey,
            @Value("${GROQ_MODEL:openai/gpt-oss-120b}") String groqModel,
            @Value("${GROQ_API_URL:https://api.groq.com/openai/v1}") String groqUrl,
            @Value("${CEREBRAS_API_KEY:}") String cerebrasKey,
            @Value("${CEREBRAS_MODEL:gpt-oss-120b}") String cerebrasModel,
            @Value("${CEREBRAS_API_URL:https://api.cerebras.ai/v1}") String cerebrasUrl,
            @Value("${MISTRAL_API_KEY:}") String mistralKey,
            @Value("${MISTRAL_MODEL:mistral-small-latest}") String mistralModel,
            @Value("${MISTRAL_API_URL:https://api.mistral.ai/v1}") String mistralUrl,
            @Value("${OPENROUTER_API_KEY:}") String openrouterKey,
            @Value("${OPENROUTER_MODEL:qwen/qwen3-next-80b-a3b-instruct:free}") String openrouterModel,
            @Value("${OPENROUTER_API_URL:https://openrouter.ai/api/v1}") String openrouterUrl,
            @Value("${LLM_GROQ_DAILY_CAP:950}") int groqDailyCap,
            @Value("${LLM_GEMINI_DAILY_CAP:900}") int geminiDailyCap,
            @Value("${LLM_OPENROUTER_DAILY_CAP:45}") int openrouterDailyCap,
            GeminiProvider geminiProvider,
            ProviderHealth health,
            ProviderQuota quota,
            ObjectMapper objectMapper) {

        requireModelWithKey("groq", groqKey, groqModel);
        requireModelWithKey("cerebras", cerebrasKey, cerebrasModel);
        requireModelWithKey("mistral", mistralKey, mistralModel);
        requireModelWithKey("openrouter", openrouterKey, openrouterModel);
        requirePositiveCap("LLM_GROQ_DAILY_CAP", groqDailyCap);
        requirePositiveCap("LLM_GEMINI_DAILY_CAP", geminiDailyCap);
        requirePositiveCap("LLM_OPENROUTER_DAILY_CAP", openrouterDailyCap);

        Map<String, LlmProvider> providers = new LinkedHashMap<>();
        providers.put("groq", new OpenAiCompatProvider(new OpenAiCompatProvider.Config(
                "groq", groqKey, groqUrl, groqModel,
                "max_completion_tokens", "low", false, false), objectMapper));
        providers.put("cerebras", new OpenAiCompatProvider(new OpenAiCompatProvider.Config(
                "cerebras", cerebrasKey, cerebrasUrl, cerebrasModel,
                "max_completion_tokens", "low", false, false), objectMapper));
        providers.put("mistral", new OpenAiCompatProvider(new OpenAiCompatProvider.Config(
                "mistral", mistralKey, mistralUrl, mistralModel,
                "max_tokens", null, false, false), objectMapper));
        providers.put("openrouter", new OpenAiCompatProvider(new OpenAiCompatProvider.Config(
                "openrouter", openrouterKey, openrouterUrl, openrouterModel,
                "max_tokens", null, true, true), objectMapper));
        providers.put("gemini", geminiProvider);

        List<String> chainIds = parseChain(chainSpec, KNOWN_PROVIDERS);
        List<LlmProvider> chain = chainIds.stream().map(providers::get).toList();

        logChainSummary(chain);
        return new LlmRouter(chain, health, quota, objectMapper);
    }

    /** Parses the chain spec into a deduped, validated, order-preserving id list. Fails on typos. */
    static List<String> parseChain(String spec, Set<String> knownIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String raw : spec.split(",")) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            if (!knownIds.contains(id)) {
                throw new IllegalStateException("Unknown provider '" + id + "' in LLM_PROVIDER_CHAIN"
                        + " — known providers: " + knownIds);
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("LLM_PROVIDER_CHAIN is empty — list at least one provider"
                    + " (or leave the variable unset for the default chain)");
        }
        return List.copyOf(ids);
    }

    /** A key without a model is a misconfiguration, not a disabled provider — fail startup. */
    static void requireModelWithKey(String id, String apiKey, String model) {
        if (apiKey != null && !apiKey.isBlank() && (model == null || model.isBlank())) {
            throw new IllegalStateException(id + " has an API key but a blank model"
                    + " — set the " + id.toUpperCase(Locale.ROOT) + "_MODEL variable");
        }
    }

    /** A cap ≤ 0 would silently make the provider permanently unavailable — fail startup. */
    static void requirePositiveCap(String name, int cap) {
        if (cap <= 0) {
            throw new IllegalStateException(name + " must be > 0"
                    + " (remove the provider from LLM_PROVIDER_CHAIN to disable it instead)");
        }
    }

    private static void logChainSummary(List<LlmProvider> chain) {
        StringJoiner summary = new StringJoiner(" | ");
        long active = 0;
        for (LlmProvider provider : chain) {
            summary.add(provider.id() + (provider.isConfigured() ? " ok" : " no-key"));
            if (provider.isConfigured()) {
                active++;
            }
        }
        log.info("[llm] provider chain: {}", summary);
        if (active == 0) {
            log.warn("[llm] no providers configured — chat/recruiter endpoints will return 503");
        } else {
            log.info("[llm] {} of {} providers active; AI endpoints enabled", active, chain.size());
        }
    }
}
