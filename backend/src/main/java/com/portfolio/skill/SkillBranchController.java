package com.portfolio.skill;

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

@Tag(name = "Skill Branches", description = "Skill branches and their nested skills")
@RestController
@RequestMapping("/api/skills/branches")
public class SkillBranchController {

    private final SkillBranchRepository branchRepository;
    private final SkillRepository skillRepository;

    public SkillBranchController(SkillBranchRepository branchRepository, SkillRepository skillRepository) {
        this.branchRepository = branchRepository;
        this.skillRepository = skillRepository;
    }

    // ── Branches ──────────────────────────────────────────────────────────────

    @Operation(summary = "List skill branches", description = "Returns all branches with their skills, ordered by offset")
    @ApiResponse(responseCode = "200", description = "Branch list returned")
    @GetMapping
    public List<SkillBranchDto> listBranches() {
        return branchRepository.findAllWithSkills()
                .stream()
                .map(SkillBranchDto::from)
                .toList();
    }

    @Operation(summary = "Create skill branch")
    @ApiResponse(responseCode = "201", description = "Branch created")
    @PostMapping
    public ResponseEntity<SkillBranchDto> createBranch(@Valid @RequestBody SkillBranchRequest req) {
        SkillBranch branch = new SkillBranch();
        branch.setBranchName(req.branchName());
        branch.setColor(req.color());
        branch.setOffset(req.offset() == null ? 0 : req.offset());
        SkillBranch saved = branchRepository.save(branch);
        return ResponseEntity.status(HttpStatus.CREATED).body(SkillBranchDto.from(saved));
    }

    @Operation(summary = "Update skill branch")
    @ApiResponse(responseCode = "200", description = "Branch updated")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @PatchMapping("/{id}")
    public SkillBranchDto updateBranch(@PathVariable UUID id, @Valid @RequestBody SkillBranchRequest req) {
        SkillBranch branch = branchRepository.findByIdWithSkills(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        branch.setBranchName(req.branchName());
        branch.setColor(req.color());
        branch.setOffset(req.offset() == null ? 0 : req.offset());
        return SkillBranchDto.from(branchRepository.save(branch));
    }

    @Operation(summary = "Delete skill branch", description = "Deletes a branch and cascades to its skills")
    @ApiResponse(responseCode = "200", description = "Branch deleted")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @DeleteMapping("/{id}")
    public Map<String, Boolean> deleteBranch(@PathVariable UUID id) {
        if (!branchRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        }
        branchRepository.deleteById(id);
        return Map.of("ok", true);
    }

    // ── Nested skills ───────────────────────────────────────────────────────────

    @Operation(summary = "Add skill to branch")
    @ApiResponse(responseCode = "201", description = "Skill created")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @PostMapping("/{id}/skills")
    public ResponseEntity<SkillDto> createSkill(@PathVariable UUID id, @Valid @RequestBody SkillRequest req) {
        SkillBranch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        Skill skill = new Skill();
        skill.setName(req.name());
        skill.setLevel(req.level());
        skill.setTag(req.tag());
        skill.setIcon(req.icon());
        skill.setBranch(branch);
        Skill saved = skillRepository.save(skill);
        return ResponseEntity.status(HttpStatus.CREATED).body(SkillDto.from(saved));
    }

    @Operation(summary = "Update skill")
    @ApiResponse(responseCode = "200", description = "Skill updated")
    @ApiResponse(responseCode = "404", description = "Skill not found")
    @PatchMapping("/{id}/skills/{sid}")
    public SkillDto updateSkill(@PathVariable UUID id, @PathVariable UUID sid, @Valid @RequestBody SkillRequest req) {
        Skill skill = skillRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
        skill.setName(req.name());
        skill.setLevel(req.level());
        skill.setTag(req.tag());
        skill.setIcon(req.icon());
        return SkillDto.from(skillRepository.save(skill));
    }

    @Operation(summary = "Delete skill")
    @ApiResponse(responseCode = "200", description = "Skill deleted")
    @ApiResponse(responseCode = "404", description = "Skill not found")
    @DeleteMapping("/{id}/skills/{sid}")
    public Map<String, Boolean> deleteSkill(@PathVariable UUID id, @PathVariable UUID sid) {
        if (!skillRepository.existsById(sid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
        }
        skillRepository.deleteById(sid);
        return Map.of("ok", true);
    }
}
