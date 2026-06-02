package com.portfolio.contact;

import com.portfolio.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends contact emails via the Resend REST API ({@code POST /emails}) using {@link WebClient}.
 * Ports {@code lib/resend.ts} + the HTML template from {@code actions/contact.ts}.
 *
 * <p>Recipient mirrors the original {@code CONTACT_TO_EMAIL ?? profile.email}: the env var if
 * set, else the seeded profile's email. The endpoint URL is overridable via
 * {@code RESEND_API_URL} (defaults to Resend) to ease testing.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String FROM = "Portfolio Contact <onboarding@resend.dev>";

    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String configuredToEmail;
    private final ProfileRepository profileRepository;

    public EmailService(@Value("${RESEND_API_KEY:}") String apiKey,
                        @Value("${RESEND_API_URL:https://api.resend.com/emails}") String apiUrl,
                        @Value("${CONTACT_TO_EMAIL:}") String configuredToEmail,
                        ProfileRepository profileRepository) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.configuredToEmail = configuredToEmail;
        this.profileRepository = profileRepository;
        this.webClient = WebClient.builder().build();
    }

    /** Returns true if the email was accepted by Resend; false on misconfiguration or failure. */
    public boolean send(String name, String email, String message) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[contact] RESEND_API_KEY is not set — cannot send email");
            return false;
        }
        String toEmail = resolveRecipient();
        if (toEmail == null || toEmail.isBlank()) {
            log.error("[contact] No recipient — set CONTACT_TO_EMAIL or seed a profile email");
            return false;
        }
        try {
            webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "from", FROM,
                            "to", List.of(toEmail),
                            "reply_to", email,
                            "subject", "[Portfolio] New message from " + name,
                            "html", buildHtml(name, email, message)))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(15));
            return true;
        } catch (Exception e) {
            log.error("[contact] Email send failed: {}", e.getMessage());
            return false;
        }
    }

    private String resolveRecipient() {
        if (configuredToEmail != null && !configuredToEmail.isBlank()) {
            return configuredToEmail;
        }
        return profileRepository.findAll().stream().findFirst().map(p -> p.getEmail()).orElse(null);
    }

    private static String buildHtml(String name, String email, String message) {
        String safeName = HtmlUtils.htmlEscape(name);
        String safeEmail = HtmlUtils.htmlEscape(email);
        String safeMessage = HtmlUtils.htmlEscape(message);
        return """
                <div style="font-family: 'JetBrains Mono', monospace; background: #0d1117; color: #e6edf3; padding: 24px; border-radius: 8px; border: 1px solid #30363d;">
                  <h2 style="color: #00ff88; margin: 0 0 16px;">$ git send-email --incoming</h2>
                  <p style="color: #8b949e; margin: 0 0 16px;">New message from your portfolio contact form</p>
                  <hr style="border: none; border-top: 1px solid #30363d; margin: 16px 0;" />
                  <p><span style="color: #58a6ff;">From:</span> %s &lt;%s&gt;</p>
                  <p><span style="color: #58a6ff;">Date:</span> %s</p>
                  <hr style="border: none; border-top: 1px solid #30363d; margin: 16px 0;" />
                  <pre style="white-space: pre-wrap; color: #c9d1d9; line-height: 1.6;">%s</pre>
                  <hr style="border: none; border-top: 1px solid #30363d; margin: 16px 0;" />
                  <p style="color: #484f58; font-size: 12px;">Reply directly to this email to respond to %s</p>
                </div>
                """.formatted(safeName, safeEmail, Instant.now().toString(), safeMessage, safeName);
    }
}
