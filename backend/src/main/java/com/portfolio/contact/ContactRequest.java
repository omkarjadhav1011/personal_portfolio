package com.portfolio.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contact form payload. Mirrors {@code contactSchema} (Zod). {@code honeypot} carries no
 * constraint — a filled value is handled as a silent bot-drop in the controller (not a
 * validation error).
 */
public record ContactRequest(
        @NotBlank(message = "Name must be at least 2 characters")
        @Size(min = 2, max = 100, message = "Name must be at least 2 characters")
        String name,

        @NotBlank(message = "Please enter a valid email address")
        @Email(message = "Please enter a valid email address")
        @Size(max = 255, message = "Email is too long")
        String email,

        @NotBlank(message = "Message must be at least 10 characters")
        @Size(min = 10, max = 2000, message = "Message must be at least 10 characters")
        String message,

        String honeypot
) {
}
