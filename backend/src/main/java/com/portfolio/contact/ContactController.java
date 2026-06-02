package com.portfolio.contact;

import com.portfolio.chatbot.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
 * Public contact endpoint. Ports the Next.js {@code actions/contact.ts}: validates the
 * payload, drops bot submissions via the honeypot, and (PHASE 4.1) logs the message
 * instead of sending it — real delivery (Resend/SMTP) lands in 4.2. Always returns
 * {@code {success, message}} with HTTP 200, matching the original server action.
 */
@Tag(name = "Contact", description = "Public contact form")
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private static final String SUCCESS_MESSAGE = "Message delivered to origin/inbox";
    private static final String FAILURE_MESSAGE = "fatal: failed to connect to remote — try again later";
    private static final String RATE_LIMIT_KEY_PREFIX = "contact:";

    private final EmailService emailService;
    private final RateLimiter rateLimiter;

    public ContactController(EmailService emailService, RateLimiter rateLimiter) {
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
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

        // Honeypot: bots fill this; humans don't. Silently succeed — no send.
        if (req.honeypot() != null && !req.honeypot().isBlank()) {
            return new ContactResult(true, SUCCESS_MESSAGE);
        }

        boolean sent = emailService.send(
                req.name().trim(), req.email().trim().toLowerCase(), req.message().trim());

        return sent
                ? new ContactResult(true, SUCCESS_MESSAGE)
                : new ContactResult(false, FAILURE_MESSAGE);
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
