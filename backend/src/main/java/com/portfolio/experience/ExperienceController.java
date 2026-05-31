package com.portfolio.experience;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Experience", description = "Work experience / timeline entries")
@RestController
@RequestMapping("/api/experience")
public class ExperienceController {

    private final ExperienceRepository repository;

    public ExperienceController(ExperienceRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List experience entries", description = "Returns all experience entries ordered by sort order")
    @ApiResponse(responseCode = "200", description = "Experience list returned")
    @GetMapping
    public List<ExperienceDto> list() {
        return repository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(ExperienceDto::from)
                .toList();
    }

    @Operation(summary = "Create experience entry", description = "Creates a new experience / timeline entry")
    @ApiResponse(responseCode = "201", description = "Entry created")
    @PostMapping
    public ResponseEntity<ExperienceDto> create(@Valid @RequestBody ExperienceRequest req) {
        CommitEntry entry = new CommitEntry();
        apply(entry, req);
        entry.setSortOrder(repository.findMaxSortOrder() + 1);
        CommitEntry saved = repository.save(entry);
        return ResponseEntity.status(HttpStatus.CREATED).body(ExperienceDto.from(saved));
    }

    @Operation(summary = "Update experience entry", description = "Updates an existing experience entry by ID")
    @ApiResponse(responseCode = "200", description = "Entry updated")
    @ApiResponse(responseCode = "404", description = "Entry not found")
    @PatchMapping("/{id}")
    public ExperienceDto update(@PathVariable UUID id, @Valid @RequestBody ExperienceRequest req) {
        CommitEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Experience entry not found"));
        apply(entry, req);
        return ExperienceDto.from(repository.save(entry));
    }

    @Operation(summary = "Delete experience entry", description = "Deletes an experience entry by ID")
    @ApiResponse(responseCode = "200", description = "Entry deleted")
    @ApiResponse(responseCode = "404", description = "Entry not found")
    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Experience entry not found");
        }
        repository.deleteById(id);
        return Map.of("ok", true);
    }

    /** Maps request fields onto an entity. {@code sortOrder} is managed separately. */
    private static void apply(CommitEntry entry, ExperienceRequest req) {
        entry.setHash(req.hash());
        entry.setType(req.type());
        entry.setTitle(req.title());
        entry.setOrg(req.org());
        entry.setDate(req.date());
        entry.setDateEnd(blankToNull(req.dateEnd()));
        entry.setDescription(req.description());
        entry.setBranch(req.branch());
        entry.setBranchColor(req.branchColor());
        entry.setColorKey(blankToNull(req.colorKey()));
        entry.setTags(req.tags());
        entry.setUrl(blankToNull(req.url()));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
