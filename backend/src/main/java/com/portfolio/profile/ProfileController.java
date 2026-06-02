package com.portfolio.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@Tag(name = "Profile", description = "Singleton developer profile")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileRepository repository;
    private final AvatarStorageService avatarStorage;

    public ProfileController(ProfileRepository repository, AvatarStorageService avatarStorage) {
        this.repository = repository;
        this.avatarStorage = avatarStorage;
    }

    @Operation(summary = "Get profile", description = "Returns the singleton developer profile, or null if not yet created")
    @ApiResponse(responseCode = "200", description = "Profile returned (may be null)")
    @GetMapping
    public ProfileDto get() {
        return repository.findAll().stream()
                .findFirst()
                .map(ProfileDto::from)
                .orElse(null);
    }

    @Operation(summary = "Upload avatar", description = "Replaces the profile picture; returns the public URL")
    @ApiResponse(responseCode = "200", description = "Avatar stored; avatarUrl returned")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        Profile profile = repository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Save your profile first before uploading a photo"));
        String avatarUrl = avatarStorage.store(file);
        profile.setAvatarUrl(avatarUrl);
        repository.save(profile);
        return Map.of("avatarUrl", avatarUrl);
    }

    @Operation(summary = "Upsert profile", description = "Updates the existing profile row, or creates the first one")
    @ApiResponse(responseCode = "200", description = "Profile saved and returned")
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
