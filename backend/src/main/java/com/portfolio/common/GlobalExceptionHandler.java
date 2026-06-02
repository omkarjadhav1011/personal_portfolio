package com.portfolio.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Normalises all error responses to {@code {"error":{"code":"...","message":"..."}}} so the
 * frontend never receives a Spring Boot white-label page, an HTML fragment, or a raw stack trace.
 *
 * <p>Priority: controller-local {@code @ExceptionHandler} methods (e.g. ContactController's
 * validation handler) take precedence over this advice — their behaviour is unchanged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorDetail(String code, String message) {}
    public record ErrorBody(ErrorDetail error) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorBody(new ErrorDetail(
                        statusToCode(ex.getStatusCode()),
                        ex.getReason() != null ? ex.getReason() : "Request failed")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ErrorBody(new ErrorDetail("INVALID_REQUEST", message)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorBody(new ErrorDetail("INTERNAL_ERROR", "An unexpected error occurred")));
    }

    private static String statusToCode(HttpStatusCode status) {
        if (status instanceof HttpStatus hs) {
            return hs.name();
        }
        return "HTTP_" + status.value();
    }
}
