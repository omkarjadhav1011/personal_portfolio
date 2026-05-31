package com.portfolio.admin;

import com.portfolio.common.Sortable;
import com.portfolio.experience.ExperienceRepository;
import com.portfolio.project.ProjectRepository;
import com.portfolio.skill.SkillDiffRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk/relative reordering for the three sortable resources. Ports the Next.js
 * {@code PATCH /api/admin/reorder} route (type ∈ stack|projects|experience), supporting
 * both a bulk {@code items:[{id,position}]} payload and an adjacent {@code id+direction}
 * swap that normalizes non-contiguous orders first. No auth (Phase 3).
 */
@Tag(name = "Reorder", description = "Bulk/relative reordering of sortable resources")
@RestController
@RequestMapping("/api/admin/reorder")
public class ReorderController {

    private static final Set<String> VALID_TYPES = Set.of("stack", "projects", "experience");

    private final ProjectRepository projectRepository;
    private final ExperienceRepository experienceRepository;
    private final SkillDiffRepository skillDiffRepository;

    public ReorderController(ProjectRepository projectRepository,
                             ExperienceRepository experienceRepository,
                             SkillDiffRepository skillDiffRepository) {
        this.projectRepository = projectRepository;
        this.experienceRepository = experienceRepository;
        this.skillDiffRepository = skillDiffRepository;
    }

    @Operation(summary = "Reorder a sortable resource",
            description = "Bulk set positions, or swap an item with its neighbor (up/down)")
    @ApiResponse(responseCode = "200", description = "Reordered")
    @ApiResponse(responseCode = "400", description = "Invalid type/payload or cannot move further")
    // Mapped to PATCH (the Next.js method) and POST (per the migration step heading).
    @RequestMapping(method = {RequestMethod.PATCH, RequestMethod.POST})
    @Transactional
    public Map<String, Boolean> reorder(@RequestBody ReorderRequest body) {
        if (body.type() == null || !VALID_TYPES.contains(body.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type");
        }
        return switch (body.type()) {
            case "projects" -> handle(projectRepository,
                    projectRepository.findAllByOrderBySortOrderAscCreatedAtAsc(), body);
            case "experience" -> handle(experienceRepository,
                    experienceRepository.findAllByOrderBySortOrderAscCreatedAtAsc(), body);
            case "stack" -> handle(skillDiffRepository,
                    skillDiffRepository.findAllByOrderBySortOrderAscCreatedAtAsc(), body);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type");
        };
    }

    private <T extends Sortable> Map<String, Boolean> handle(JpaRepository<T, UUID> repo,
                                                             List<T> ordered,
                                                             ReorderRequest body) {
        if (body.items() != null) {
            bulkReorder(repo, body.items());
        } else {
            if (body.id() == null || !("up".equals(body.direction()) || "down".equals(body.direction()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request");
            }
            boolean moved = adjacentSwap(repo, ordered, UUID.fromString(body.id()), body.direction());
            if (!moved) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot move further");
            }
        }
        return Map.of("success", true);
    }

    /** Sets each listed entity's sort_order to its given position. */
    private <T extends Sortable> void bulkReorder(JpaRepository<T, UUID> repo, List<ReorderRequest.ReorderItem> items) {
        List<T> toSave = new ArrayList<>();
        for (ReorderRequest.ReorderItem item : items) {
            if (item.id() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid items payload");
            }
            repo.findById(UUID.fromString(item.id())).ifPresent(entity -> {
                entity.setSortOrder(item.position());
                toSave.add(entity);
            });
        }
        repo.saveAll(toSave);
    }

    /** Swaps an item with its up/down neighbor, normalizing to contiguous 1-based orders first. */
    private <T extends Sortable> boolean adjacentSwap(JpaRepository<T, UUID> repo, List<T> ordered,
                                                      UUID id, String direction) {
        boolean hasGaps = false;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getSortOrder() != i + 1) {
                hasGaps = true;
                break;
            }
        }
        if (hasGaps) {
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).setSortOrder(i + 1);
            }
            repo.saveAll(ordered);
        }

        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(id)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) {
            return false;
        }
        int swapIdx = "up".equals(direction) ? idx - 1 : idx + 1;
        if (swapIdx < 0 || swapIdx >= ordered.size()) {
            return false;
        }

        T current = ordered.get(idx);
        T neighbor = ordered.get(swapIdx);
        int currentOrder = current.getSortOrder();
        int neighborOrder = neighbor.getSortOrder();
        current.setSortOrder(neighborOrder);
        neighbor.setSortOrder(currentOrder);
        repo.saveAll(List.of(current, neighbor));
        return true;
    }
}
