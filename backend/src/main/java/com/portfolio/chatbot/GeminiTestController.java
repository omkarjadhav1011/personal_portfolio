package com.portfolio.chatbot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Test endpoint for Gemini integration; returns the model's reply to a fixed prompt + user question. */
@RestController
public class GeminiTestController {

    private final GeminiClient geminiClient;

    public GeminiTestController(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @GetMapping("/api/_gemini-test")
    public Map<String, String> test(
            @RequestParam(name = "q", defaultValue = "Say hello in five words.") String q) {
        String reply = geminiClient.generateContent(
                "You are a terse assistant. Reply briefly.",
                List.of(new ChatMessage("user", q)));
        return Map.of("reply", reply);
    }
}
