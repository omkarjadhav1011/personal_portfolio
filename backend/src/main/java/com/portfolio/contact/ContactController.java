package com.portfolio.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private static final String SUCCESS_MESSAGE = "Message delivered to origin/inbox";

    @Operation(summary = "Send a contact message", description = "Public; validates, drops bots, logs (no send yet)")
    @ApiResponse(responseCode = "200", description = "Accepted (or validation/bot result in the body)")
    @PostMapping
    public ContactResult send(@Valid @RequestBody ContactRequest req) {
        // Honeypot: bots fill this; humans don't. Silently succeed — no log, no send.
        if (req.honeypot() != null && !req.honeypot().isBlank()) {
            return new ContactResult(true, SUCCESS_MESSAGE);
        }

        // PHASE 4.1: log instead of sending.
        log.info("[contact] message from {} <{}>: {}",
                req.name().trim(), req.email().trim().toLowerCase(), req.message().trim());

        return new ContactResult(true, SUCCESS_MESSAGE);
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
