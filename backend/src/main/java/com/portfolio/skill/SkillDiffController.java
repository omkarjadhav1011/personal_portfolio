package com.portfolio.skill;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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

@Tag(name = "Skill Diffs", description = "Flat skill-diff list (added/deprecated/modified)")
@RestController
@RequestMapping("/api/skills/diff")
public class SkillDiffController {

    private final SkillDiffRepository repository;

    public SkillDiffController(SkillDiffRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List skill diffs", description = "Returns all diffs ordered by sort order")
    @ApiResponse(responseCode = "200", description = "Diff list returned")
    @GetMapping
    public List<SkillDiffDto> list() {
        return repository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(SkillDiffDto::from)
                .toList();
    }

    @Operation(summary = "Create skill diff")
    @ApiResponse(responseCode = "201", description = "Diff created")
    @ApiResponse(responseCode = "409", description = "A diff with this name already exists")
    @PostMapping
    public ResponseEntity<SkillDiffDto> create(@Valid @RequestBody SkillDiffRequest req) {
        SkillDiff diff = new SkillDiff();
        diff.setName(req.name());
        diff.setType(req.type());
        diff.setNote(req.note());
        diff.setSortOrder(repository.findMaxSortOrder() + 1);
        try {
            SkillDiff saved = repository.saveAndFlush(diff);
            return ResponseEntity.status(HttpStatus.CREATED).body(SkillDiffDto.from(saved));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A diff with this name already exists");
        }
    }

    @Operation(summary = "Update skill diff")
    @ApiResponse(responseCode = "200", description = "Diff updated")
    @ApiResponse(responseCode = "404", description = "Diff not found")
    @PatchMapping("/{id}")
    public SkillDiffDto update(@PathVariable UUID id, @Valid @RequestBody SkillDiffRequest req) {
        SkillDiff diff = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diff not found"));
        diff.setName(req.name());
        diff.setType(req.type());
        diff.setNote(req.note());
        return SkillDiffDto.from(repository.save(diff));
    }

    @Operation(summary = "Delete skill diff")
    @ApiResponse(responseCode = "200", description = "Diff deleted")
    @ApiResponse(responseCode = "404", description = "Diff not found")
    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diff not found");
        }
        repository.deleteById(id);
        return Map.of("ok", true);
    }
}
