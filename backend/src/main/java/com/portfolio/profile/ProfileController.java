package com.portfolio.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Profile", description = "Singleton developer profile")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024; // 5 MB

    private static final Set<String> ALLOWED_RESUME_TYPES = Set.of(
            "application/pdf",
            "application/msword", // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"); // .docx
    private static final long MAX_RESUME_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final String DEFAULT_RESUME_FILENAME = "resume.pdf";

    private final ProfileRepository repository;
    private final ResumeTextExtractor resumeTextExtractor;

    public ProfileController(ProfileRepository repository, ResumeTextExtractor resumeTextExtractor) {
        this.repository = repository;
        this.resumeTextExtractor = resumeTextExtractor;
    }

    @Operation(summary = "Get profile")
    @GetMapping
    public ProfileDto get() {
        return repository.findAll().stream()
                .findFirst()
                .map(ProfileDto::from)
                .orElse(null);
    }

    @Operation(summary = "Upsert profile")
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
        profile.setTechPicks(req.techPicks());
        return ProfileDto.from(repository.save(profile));
    }

    @Operation(summary = "Upload avatar — stores image bytes in the database")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, GIF or WebP images are allowed");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be ≤ 5 MB");
        }
        byte[] data = file.getBytes();
        verifyMagicBytes(data, contentType);
        Profile profile = repository.findAll().stream().findFirst().orElseGet(Profile::new);
        profile.setAvatarData(data);
        profile.setAvatarContentType(contentType);
        repository.save(profile);
        return Map.of("avatarUrl", "/api/profile/avatar");
    }

    @Operation(summary = "Retrieve avatar image from the database")
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar() {
        List<Profile> all = repository.findAll();
        if (all.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Profile p = all.get(0);
        if (p.getAvatarData() == null || p.getAvatarData().length == 0) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        HttpHeaders headers = new HttpHeaders();
        String ct = p.getAvatarContentType() != null ? p.getAvatarContentType() : "image/jpeg";
        headers.setContentType(MediaType.parseMediaType(ct));
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return new ResponseEntity<>(p.getAvatarData(), headers, HttpStatus.OK);
    }

    @Operation(summary = "Upload resume — stores the document bytes in the database")
    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadResume(@RequestParam("file") MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_RESUME_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PDF, DOC or DOCX documents are allowed");
        }
        if (file.getSize() > MAX_RESUME_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be ≤ 5 MB");
        }
        byte[] data = file.getBytes();
        verifyMagicBytes(data, contentType);
        Profile profile = repository.findAll().stream().findFirst().orElseGet(Profile::new);
        profile.setResumeData(data);
        profile.setResumeContentType(contentType);
        profile.setResumeFilename(sanitizeFilename(file.getOriginalFilename()));
        // Phase F1: extract curated text for the RAG corpus (PDF only; DOC/DOCX not supported yet).
        // The D1 aspect re-indexes ProfileController writes, so the new resume text is embedded
        // automatically. The raw bytes (resumeData) are never placed in the corpus.
        profile.setResumeText("application/pdf".equals(contentType)
                ? resumeTextExtractor.extractPdfText(data).orElse(null)
                : null);
        repository.save(profile);
        return Map.of(
                "resumeUrl", "/api/profile/resume",
                "resumeFilename", profile.getResumeFilename());
    }

    @Operation(summary = "Download resume document from the database")
    @GetMapping("/resume")
    public ResponseEntity<byte[]> getResume() {
        List<Profile> all = repository.findAll();
        if (all.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Profile p = all.get(0);
        if (p.getResumeData() == null || p.getResumeData().length == 0) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        String filename = p.getResumeFilename() != null && !p.getResumeFilename().isBlank()
                ? p.getResumeFilename()
                : DEFAULT_RESUME_FILENAME;
        HttpHeaders headers = new HttpHeaders();
        String ct = p.getResumeContentType() != null ? p.getResumeContentType() : "application/pdf";
        headers.setContentType(MediaType.parseMediaType(ct));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return new ResponseEntity<>(p.getResumeData(), headers, HttpStatus.OK);
    }

    /**
     * Confirms the uploaded bytes actually start with the magic-number signature for the
     * declared {@code Content-Type}. The browser-supplied content type is trivially spoofable
     * (CWE-434), so we verify the real file format before persisting it.
     */
    private static void verifyMagicBytes(byte[] data, String contentType) {
        boolean ok = switch (contentType) {
            case "image/jpeg" -> startsWith(data, 0xFF, 0xD8, 0xFF);
            case "image/png"  -> startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif"  -> startsWith(data, 0x47, 0x49, 0x46, 0x38); // "GIF8"
            case "image/webp" -> startsWith(data, 0x52, 0x49, 0x46, 0x46)   // "RIFF"
                    && regionMatches(data, 8, 0x57, 0x45, 0x42, 0x50);      // "WEBP"
            case "application/pdf" -> startsWith(data, 0x25, 0x50, 0x44, 0x46); // "%PDF"
            // Legacy .doc is an OLE compound file.
            case "application/msword" -> startsWith(data, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            // .docx is a ZIP container ("PK\x03\x04").
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    startsWith(data, 0x50, 0x4B, 0x03, 0x04);
            default -> false;
        };
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File content does not match its declared type");
        }
    }

    private static boolean startsWith(byte[] data, int... signature) {
        return regionMatches(data, 0, signature);
    }

    private static boolean regionMatches(byte[] data, int offset, int... signature) {
        if (data == null || data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Strips any directory components a browser may include so we store a bare file name. */
    private static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return DEFAULT_RESUME_FILENAME;
        }
        String name = original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.isEmpty() ? DEFAULT_RESUME_FILENAME : name;
    }
}
