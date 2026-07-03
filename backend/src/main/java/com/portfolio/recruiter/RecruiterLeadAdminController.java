package com.portfolio.recruiter;

import com.portfolio.contact.MessageStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN-only view over {@code recruiter_lead} (Phase C3) — the "Leads" tab of the admin inbox.
 * {@code /api/admin/**} is ADMIN-gated by SecurityConfig (matched before the public GET
 * catch-all — pinned by SecurityConfigTest), so every route here requires a valid JWT.
 * Responds with DTOs, never entities. Triage only (GET + PATCH): leads are follow-up
 * material, not content to curate — there is deliberately no DELETE.
 */
@Tag(name = "Leads admin", description = "Recruiter-lead inbox (ADMIN)")
@RestController
@RequestMapping("/api/admin/leads")
public class RecruiterLeadAdminController {

    private static final int MAX_PAGE_SIZE = 200;

    private final RecruiterLeadRepository repository;

    public RecruiterLeadAdminController(RecruiterLeadRepository repository) {
        this.repository = repository;
    }

    /** Page envelope — Spring's {@code Page} is not serialized directly (unstable JSON shape). */
    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {
    }

    public record UpdateStatusRequest(String status) {
    }

    @Operation(summary = "List leads, newest first, optionally filtered by status")
    @GetMapping
    public PageResponse<RecruiterLeadDto> list(@RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RecruiterLead> result = status == null || status.isBlank()
                ? repository.findAll(pageable)
                : repository.findByStatus(parseStatus(status), pageable);
        return new PageResponse<>(result.map(RecruiterLeadDto::from).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Operation(summary = "Update a lead's status (e.g. mark READ)")
    @PatchMapping("/{id}")
    public RecruiterLeadDto updateStatus(@PathVariable UUID id,
                                         @RequestBody UpdateStatusRequest req) {
        RecruiterLead lead = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        lead.setStatus(parseStatus(req.status()));
        return RecruiterLeadDto.from(repository.save(lead));
    }

    /** Parsed here (not bound as an enum) so a bad value is a clean 400, not a generic 500. */
    private static MessageStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        try {
            return MessageStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + raw);
        }
    }
}
