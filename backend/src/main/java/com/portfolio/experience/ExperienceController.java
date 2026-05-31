package com.portfolio.experience;

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

@RestController
@RequestMapping("/api/experience")
public class ExperienceController {

    private final ExperienceRepository repository;

    public ExperienceController(ExperienceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ExperienceDto> list() {
        return repository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(ExperienceDto::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ExperienceDto> create(@Valid @RequestBody ExperienceRequest req) {
        CommitEntry entry = new CommitEntry();
        apply(entry, req);
        entry.setSortOrder(repository.findMaxSortOrder() + 1);
        CommitEntry saved = repository.save(entry);
        return ResponseEntity.status(HttpStatus.CREATED).body(ExperienceDto.from(saved));
    }

    @PatchMapping("/{id}")
    public ExperienceDto update(@PathVariable UUID id, @Valid @RequestBody ExperienceRequest req) {
        CommitEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Experience entry not found"));
        apply(entry, req);
        return ExperienceDto.from(repository.save(entry));
    }

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
