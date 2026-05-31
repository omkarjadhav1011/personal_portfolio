package com.portfolio.profile;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileRepository repository;

    public ProfileController(ProfileRepository repository) {
        this.repository = repository;
    }

    /** Returns the single profile, or null when none exists yet (mirrors the Next.js GET). */
    @GetMapping
    public ProfileDto get() {
        return repository.findAll().stream()
                .findFirst()
                .map(ProfileDto::from)
                .orElse(null);
    }

    /** Upserts the singleton profile: updates the existing row, or creates the first one. */
    @PatchMapping
    public ProfileDto update(@Valid @RequestBody ProfileRequest req) {
        Profile profile = repository.findAll().stream().findFirst().orElseGet(Profile::new);
        profile.setName(req.name());
        profile.setHandle(req.handle());
        profile.setHeadline(req.headline());
        profile.setBio(req.bio());
        profile.setCurrentBranch(req.currentBranch());
        profile.setCurrentStatus(req.currentStatus());
        profile.setAvailableForWork(req.availableForWork() == null || req.availableForWork());
        profile.setEmail(req.email());
        profile.setLocation(req.location());
        profile.setSocials(req.socials());
        profile.setFunFacts(req.funFacts());
        profile.setStash(req.stash());
        profile.setCurrentRole(req.currentRole());
        return ProfileDto.from(repository.save(profile));
    }
}
