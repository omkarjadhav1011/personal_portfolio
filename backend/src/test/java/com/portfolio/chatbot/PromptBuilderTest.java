package com.portfolio.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.profile.SocialLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder(new ObjectMapper());

    @Test
    void buildsPromptWithExpectedSectionsAndData() {
        PortfolioContext ctx = sampleContext();
        String prompt = builder.buildSystemPrompt(ctx);

        // Name interpolated
        assertTrue(prompt.contains("AI assistant for Omkar Jadhav's portfolio"), "name in intro");

        // Major sections present
        assertTrue(prompt.contains("<portfolio_data>"), "portfolio_data block");
        assertTrue(prompt.contains("# Topic rules"), "topic rules section");
        assertTrue(prompt.contains("# How to answer ON-TOPIC questions"), "on-topic section");
        assertTrue(prompt.contains("# How to answer OFF-TOPIC questions"), "off-topic section");
        assertTrue(prompt.contains("# Hard secrecy rules — never violate"), "secrecy section");

        // Context JSON embedded (a project slug + profile handle from the data)
        assertTrue(prompt.contains("\"slug\":\"my-proj\""), "project slug in JSON");
        assertTrue(prompt.contains("\"handle\":\"omkarjadhav\""), "profile handle in JSON");
    }

    private PortfolioContext sampleContext() {
        var profile = new PortfolioContext.ProfileSummary(
                "Omkar Jadhav", "omkarjadhav", "Full-Stack Dev", "Builder.",
                "main", "Available", true, "omkar@example.com", "Pune, India",
                List.of(new SocialLink("GitHub", "https://github.com/x", "github")),
                List.of("Loves git"), List.of());
        var project = new PortfolioContext.ProjectSummary(
                "my-proj", "repo", "desc", null, "Java", List.of("java"),
                1, 0, 0, "active", true, null, null, "2026", "msg");
        return new PortfolioContext(profile, List.of(project), List.of(), List.of(), List.of());
    }
}
