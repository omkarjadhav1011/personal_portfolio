package com.portfolio.query;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContext.ProfileSummary;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.profile.SocialLink;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase A1 — verifies the shared {@link PortfolioQueryService} facade: (1) {@link ProfileView}'s
 * shape exposes no sensitive field (defense by construction, mirroring
 * {@code chatbot.CorpusBoundaryTest}), and (2) {@code getProfile()} maps only the curated public
 * fields out of the underlying context, dropping bio/email/icon. A pure unit test — the context
 * service is mocked, so no Spring context or DB is needed.
 */
class PortfolioQueryServiceTest {

    /** Lowercased field-name fragments that must NEVER appear anywhere in a tool's output shape. */
    private static final List<String> FORBIDDEN = List.of(
            "password", "secret", "hash", "apikey", "token", "credential",
            "avatardata", "resumedata", "rawbytes", "bytes",
            "createdat", "updatedat", "deletedat", "internal");

    @Test
    void profileViewExposesNoSensitiveFields() {
        List<String> names = new ArrayList<>();
        collectComponentNames(ProfileView.class, names, new HashSet<>());
        assertTrue(names.size() >= 5, "should have discovered the ProfileView record components");

        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String bad : FORBIDDEN) {
                assertFalse(lower.contains(bad),
                        "ProfileView field '" + name + "' matches forbidden fragment '" + bad
                                + "' — a tool output must never expose sensitive data.");
            }
            assertNotEquals("id", lower, "raw entity id must not be exposed by a tool view");
            assertNotEquals("uuid", lower, "raw uuid must not be exposed by a tool view");
        }
    }

    @Test
    void getProfileMapsOnlyPublicFields() {
        // bio, email, fun facts, stash, and the link icon are present in the source summary but
        // must NOT survive into the public ProfileView.
        ProfileSummary summary = new ProfileSummary(
                "Omkar Jadhav", "omkarj", "Backend Engineer", "private-ish bio text",
                "main", "shipping features", true,
                "omkar@example.com", "Pune, India",
                List.of(new SocialLink("GitHub", "https://github.com/omkar", "github-icon")),
                List.of("fun fact"), List.of("stash item"));
        PortfolioContext ctx = new PortfolioContext(
                summary, List.of(), List.of(), List.of(), List.of(), "extracted resume text");

        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(ctx);

        ProfileView view = new PortfolioQueryService(contextService).getProfile();

        assertEquals("Omkar Jadhav", view.name());
        assertEquals("Backend Engineer", view.headline());
        assertEquals("main", view.currentBranch());
        assertEquals("shipping features", view.currentStatus());
        assertTrue(view.availableForWork());
        assertEquals("Pune, India", view.location());
        assertEquals(1, view.links().size());
        assertEquals("GitHub", view.links().get(0).label());
        assertEquals("https://github.com/omkar", view.links().get(0).url());
    }

    /** Recursively gathers record-component names, descending into nested records and List&lt;Record&gt;. */
    private static void collectComponentNames(Class<?> type, List<String> out, Set<Class<?>> seen) {
        if (type == null || !type.isRecord() || !seen.add(type)) {
            return;
        }
        for (RecordComponent rc : type.getRecordComponents()) {
            out.add(rc.getName());
            Class<?> rcType = rc.getType();
            if (rcType.isRecord()) {
                collectComponentNames(rcType, out, seen);
            } else if (List.class.isAssignableFrom(rcType)
                    && rc.getGenericType() instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> argClass) {
                    collectComponentNames(argClass, out, seen);
                }
            }
        }
    }
}
