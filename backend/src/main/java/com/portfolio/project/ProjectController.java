package com.portfolio.project;

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

@Tag(name = "Projects", description = "Portfolio project entries")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository repository;

    public ProjectController(ProjectRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List projects", description = "Returns all projects pinned first, then by sort order")
    @ApiResponse(responseCode = "200", description = "Project list returned")
    @GetMapping
    public List<ProjectDto> list() {
        return repository.findAllByOrderByPinnedDescSortOrderAsc()
                .stream()
                .map(ProjectDto::from)
                .toList();
    }

    @Operation(summary = "Create project", description = "Creates a new project entry appended to the sort order")
    @ApiResponse(responseCode = "201", description = "Project created")
    @PostMapping
    public ResponseEntity<ProjectDto> create(@Valid @RequestBody ProjectCreateRequest req) {
        Project p = new Project();
        apply(p, req);
        p.setSortOrder(repository.findMaxSortOrder() + 1);
        Project saved = repository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectDto.from(saved));
    }

    @Operation(summary = "Update project", description = "Updates an existing project by ID (sort order unchanged)")
    @ApiResponse(responseCode = "200", description = "Project updated")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @PatchMapping("/{id}")
    public ProjectDto update(@PathVariable UUID id, @Valid @RequestBody ProjectCreateRequest req) {
        Project p = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        apply(p, req);
        return ProjectDto.from(repository.save(p));
    }

    @Operation(summary = "Delete project", description = "Deletes a project by ID")
    @ApiResponse(responseCode = "200", description = "Project deleted")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        repository.deleteById(id);
        return Map.of("ok", true);
    }

    /** Maps request fields onto an entity. {@code sortOrder} is managed separately. */
    private static void apply(Project p, ProjectCreateRequest req) {
        p.setSlug(req.slug());
        p.setRepoName(req.repoName());
        p.setDescription(req.description());
        p.setLanguage(req.language());
        p.setLanguageColor(req.languageColor());
        p.setStars(req.stars() == null ? 0 : req.stars());
        p.setForks(req.forks() == null ? 0 : req.forks());
        p.setCommits(req.commits() == null ? 0 : req.commits());
        p.setLastCommit(req.lastCommit());
        p.setLastCommitMsg(req.lastCommitMsg());
        p.setTags(req.tags() == null ? List.of() : req.tags());
        p.setLiveUrl(blankToNull(req.liveUrl()));
        p.setRepoUrl(blankToNull(req.repoUrl()));
        p.setStatus(req.status() == null ? "active" : req.status());
        p.setPinned(req.pinned() != null && req.pinned());
        p.setLongDescription(req.longDescription());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
