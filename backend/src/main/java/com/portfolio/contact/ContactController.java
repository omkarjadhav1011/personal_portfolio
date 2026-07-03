package com.portfolio.contact;

import com.portfolio.chatbot.RateLimiter;
import com.portfolio.common.Hashing;
import com.portfolio.notify.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public contact endpoint. Validates the payload, drops bot submissions via the honeypot,
 * then <b>stores the message first</b> (Phase A1 store-then-send: Postgres is the system of
 * record) and only afterwards attempts email delivery — a Resend failure is logged, never
 * surfaced, because the lead is already safe in {@code contact_message}. Always returns
 * {@code {success, message}} with HTTP 200, matching the original server action.
 */
@Tag(name = "Contact", description = "Public contact form")
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private static final String SUCCESS_MESSAGE = "Message delivered to origin/inbox";
    private static final String RATE_LIMIT_KEY_PREFIX = "contact:";

    private final EmailService emailService;
    private final RateLimiter rateLimiter;
    private final ContactMessageRepository messageRepository;
    private final NotificationService notificationService;

    public ContactController(EmailService emailService, RateLimiter rateLimiter,
                             ContactMessageRepository messageRepository,
                             NotificationService notificationService) {
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
    }

    @Operation(summary = "Send a contact message", description = "Public; validates, drops bots, sends via Resend")
    @ApiResponse(responseCode = "200", description = "Accepted (or validation/bot/failure result in the body)")
    @PostMapping
    public ContactResult send(@Valid @RequestBody ContactRequest req,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_KEY_PREFIX + RateLimiter.clientIp(request));
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages. Please slow down.");
        }

        // Honeypot: bots fill this; humans don't. Silently succeed — no row, no send.
        if (req.honeypot() != null && !req.honeypot().isBlank()) {
            return new ContactResult(true, SUCCESS_MESSAGE);
        }

        String name = req.name().trim();
        String email = req.email().trim().toLowerCase();
        String message = req.message().trim();

        // Store first — the row is the deliverable; email is a best-effort notification.
        ContactMessage saved = messageRepository.save(new ContactMessage(
                name, email, message, resolveSource(req.source()),
                Hashing.sha256Hex(RateLimiter.clientIp(request))));

        // DB commit → notify (B2): async and fail-open, never part of the request outcome.
        notificationService.notifyOwner("📬 New message from " + name);

        boolean sent = emailService.send(name, email, message);
        if (!sent) {
            log.warn("[contact] message {} stored but email notification failed — visible in admin inbox", saved.getId());
        }

        return new ContactResult(true, SUCCESS_MESSAGE);
    }

    /**
     * The E1 chat handoff tags its submissions CHATBOT; everything else (absent, unknown, or a
     * value a client shouldn't self-assign like MCP) stays WEB — the label is a funnel hint,
     * not trusted input.
     */
    private static MessageSource resolveSource(String raw) {
        return "CHATBOT".equalsIgnoreCase(raw == null ? "" : raw.trim())
                ? MessageSource.CHATBOT
                : MessageSource.WEB;
    }

    /** Mirror the server action: validation failures return 200 with {success:false, firstError}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ContactResult handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid form data");
        return new ContactResult(false, message);
    }
}
