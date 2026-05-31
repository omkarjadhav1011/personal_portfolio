package com.portfolio.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Projects", description = "Portfolio project entries")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository repository;

    public ProjectController(ProjectRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List projects", description = "Returns all projects ordered by sort order, pinned first")
    @ApiResponse(responseCode = "200", description = "Project list returned")
    @GetMapping
    public List<ProjectDto> list() {
        return repository.findAllByOrderBySortOrderAscPinnedDesc()
                .stream()
                .map(ProjectDto::from)
                .toList();
    }

    @Operation(summary = "Create project", description = "Creates a new project entry appended to the sort order")
    @ApiResponse(responseCode = "201", description = "Project created")
    @PostMapping
    public ResponseEntity<ProjectDto> create(@Valid @RequestBody ProjectCreateRequest req) {
        Project p = new Project();
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
        p.setSortOrder(repository.findMaxSortOrder() + 1);

        Project saved = repository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectDto.from(saved));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
