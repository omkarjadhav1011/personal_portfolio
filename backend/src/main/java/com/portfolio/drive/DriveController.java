package com.portfolio.drive;

import com.portfolio.drive.DriveDtos.CreateFolderRequest;
import com.portfolio.drive.DriveDtos.FileDto;
import com.portfolio.drive.DriveDtos.FolderContentsDto;
import com.portfolio.drive.DriveDtos.FolderDto;
import com.portfolio.drive.DriveDtos.UpdateFolderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Secure Document Vault API. Every route here is ADMIN-only — enforced in {@code SecurityConfig}
 * by {@code .requestMatchers("/api/drive/**").hasRole("ADMIN")}, placed above the public GET
 * catch-all. (The single-use download endpoint is public and lives separately — a later phase.)
 *
 * <p>The whole controller is wired only when {@code STORAGE_ENDPOINT} is set, so the non-drive app
 * and the test suite boot without the storage/crypto beans this depends on.
 */
@Tag(name = "Drive", description = "Secure Document Vault (ADMIN only)")
@RestController
@RequestMapping("/api/drive")
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class DriveController {

    private final DriveService service;

    public DriveController(DriveService service) {
        this.service = service;
    }

    @Operation(summary = "List the vault root (root folders + root files)")
    @GetMapping("/folders")
    public FolderContentsDto listRoot() {
        return service.listRoot();
    }

    @Operation(summary = "List a folder's contents (subfolders + files)")
    @GetMapping("/folders/{id}")
    public FolderContentsDto listFolder(@PathVariable UUID id) {
        return service.listFolder(id);
    }

    @Operation(summary = "Create a folder")
    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public FolderDto createFolder(@Valid @RequestBody CreateFolderRequest req) {
        return service.createFolder(req.name(), req.parentId());
    }

    @Operation(summary = "Rename and/or move a folder")
    @PatchMapping("/folders/{id}")
    public FolderDto updateFolder(@PathVariable UUID id, @Valid @RequestBody UpdateFolderRequest req) {
        return service.updateFolder(id, req.name(), req.parentId());
    }

    @Operation(summary = "Delete a folder and its subtree (cascades folders, files and their objects)")
    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable UUID id) {
        service.deleteFolder(id);
    }

    @Operation(summary = "Upload a file (streamed in, encrypted, stored as ciphertext)")
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto uploadFile(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "folderId", required = false) UUID folderId,
                              @RequestParam(value = "sensitive", required = false, defaultValue = "false")
                              boolean sensitive) {
        return service.uploadFile(file, folderId, sensitive);
    }

    @Operation(summary = "Delete a file (DB row + stored object)")
    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable UUID id) {
        service.deleteFile(id);
    }
}
