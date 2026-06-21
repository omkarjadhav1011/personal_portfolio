package com.portfolio.drive;

import com.portfolio.drive.DriveDtos.DownloadTokenResponse;
import com.portfolio.drive.DriveDtos.FileDto;
import com.portfolio.drive.DriveDtos.FolderContentsDto;
import com.portfolio.drive.DriveDtos.FolderDto;
import com.portfolio.drive.EnvelopeCryptoService.EncryptedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the document vault: folder tree CRUD and encrypted file upload/delete. Bytes are
 * encrypted ({@link EnvelopeCryptoService}) before they reach object storage ({@link StorageService})
 * and only metadata is persisted ({@link DriveFileRepository}/{@link DriveFolderRepository}).
 * Gated on {@code STORAGE_ENDPOINT}, like the storage/crypto beans it depends on.
 */
@Service
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);

    /** Declared content types accepted on upload. The browser value is verified against the file's
     *  magic bytes below (CWE-434) for the binary formats that have a signature. */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf",
            "text/plain", "text/csv", "text/markdown",
            "application/json",
            "application/zip",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final String DEFAULT_FILENAME = "file";

    private final DriveFolderRepository folders;
    private final DriveFileRepository files;
    private final StorageService storage;
    private final EnvelopeCryptoService crypto;
    private final DownloadTokenService downloadTokens;
    private final EmailOtpService emailOtp;
    /** Optional: present only when email is configured (MAIL_HOST). */
    private final ObjectProvider<DriveMailService> mailProvider;

    public DriveService(DriveFolderRepository folders, DriveFileRepository files,
                        StorageService storage, EnvelopeCryptoService crypto,
                        DownloadTokenService downloadTokens, EmailOtpService emailOtp,
                        ObjectProvider<DriveMailService> mailProvider) {
        this.folders = folders;
        this.files = files;
        this.storage = storage;
        this.crypto = crypto;
        this.downloadTokens = downloadTokens;
        this.emailOtp = emailOtp;
        this.mailProvider = mailProvider;
    }

    // ── Listing ──────────────────────────────────────────────────────────────

    public FolderContentsDto listRoot() {
        return new FolderContentsDto(
                null,
                folders.findByParentIdIsNullOrderByNameAsc().stream().map(FolderDto::from).toList(),
                files.findByFolderIdIsNullOrderByOriginalFilenameAsc().stream().map(FileDto::from).toList());
    }

    public FolderContentsDto listFolder(UUID id) {
        DriveFolder folder = requireFolder(id);
        return new FolderContentsDto(
                FolderDto.from(folder),
                folders.findByParentIdOrderByNameAsc(id).stream().map(FolderDto::from).toList(),
                files.findByFolderIdOrderByOriginalFilenameAsc(id).stream().map(FileDto::from).toList());
    }

    // ── Folders ──────────────────────────────────────────────────────────────

    public FolderDto createFolder(String name, UUID parentId) {
        String trimmed = requireName(name);
        if (parentId != null) {
            requireFolder(parentId);
        }
        requireUniqueName(parentId, trimmed, null);
        return FolderDto.from(folders.save(new DriveFolder(parentId, trimmed)));
    }

    public FolderDto updateFolder(UUID id, String name, UUID newParentId) {
        DriveFolder folder = requireFolder(id);

        // Move, if the desired parent differs from the current one.
        if (!Objects.equals(folder.getParentId(), newParentId)) {
            if (newParentId != null) {
                requireFolder(newParentId);
                if (isInSubtreeOf(newParentId, id)) {
                    throw badRequest("Cannot move a folder into itself or its own subtree");
                }
            }
            folder.setParentId(newParentId);
        }

        if (name != null && !name.isBlank()) {
            folder.setName(name.trim());
        }

        requireUniqueName(folder.getParentId(), folder.getName(), id);
        return FolderDto.from(folders.save(folder));
    }

    public void deleteFolder(UUID id) {
        DriveFolder folder = requireFolder(id);
        // Collect the subtree's object keys BEFORE deleting, since the DB cascade will remove the
        // file-metadata rows (but cannot reach the object store).
        Set<UUID> subtree = collectSubtreeIds(id);
        List<String> keys = files.findByFolderIdIn(subtree).stream().map(DriveFile::getStorageKey).toList();

        folders.delete(folder); // cascade removes descendant folders + file rows

        // Best-effort object cleanup; a failure here leaks an object but keeps the DB consistent.
        for (String key : keys) {
            deleteObjectQuietly(key);
        }
    }

    // ── Files ────────────────────────────────────────────────────────────────

    public FileDto uploadFile(MultipartFile file, UUID folderId, boolean sensitive) {
        if (file == null || file.isEmpty()) {
            throw badRequest("A non-empty file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw badRequest("Unsupported file type. Allowed: images, PDF, text, Office documents, zip");
        }
        if (folderId != null) {
            requireFolder(folderId);
        }

        byte[] plain;
        try {
            plain = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read the upload");
        }
        verifyMagicBytes(plain, contentType);

        EncryptedPayload enc = crypto.encrypt(plain);
        String storageKey = UUID.randomUUID().toString();
        storage.put(storageKey, enc.ciphertext());

        DriveFile entity = new DriveFile();
        entity.setFolderId(folderId);
        entity.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        entity.setContentType(contentType);
        entity.setSizeBytes(plain.length);
        entity.setStorageKey(storageKey);
        entity.setEncIv(enc.iv());
        entity.setEncWrappedKey(enc.wrappedKey());
        entity.setSensitive(sensitive);
        try {
            return FileDto.from(files.save(entity));
        } catch (RuntimeException e) {
            // The object is already stored; if persisting metadata fails, don't leave it orphaned.
            deleteObjectQuietly(storageKey);
            throw e;
        }
    }

    public void deleteFile(UUID id) {
        DriveFile file = files.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        files.delete(file); // metadata first → never leaves a row pointing at a deleted object
        deleteObjectQuietly(file.getStorageKey());
    }

    // ── Secure download ─────────────────────────────────────────────────────────

    /** A decrypted file ready to stream to the client (internal — not a JSON DTO). */
    public record DownloadedFile(byte[] content, String filename, String contentType) {
    }

    /**
     * Issues a 5-minute single-use download token for a file (ADMIN only). A sensitive file also
     * requires a valid email OTP (see {@link #requestOtp}) — the second factor against a leaked token.
     */
    public DownloadTokenResponse issueDownloadToken(UUID id, String otp) {
        DriveFile file = requireFile(id);
        assertSensitiveAccessAllowed(file, otp);
        return new DownloadTokenResponse(downloadTokens.issue(id), downloadTokens.getExpirySeconds());
    }

    /** Emails a fresh verification code for a sensitive file to the owner's address. */
    public void requestOtp(UUID id) {
        DriveFile file = requireFile(id);
        if (!file.isSensitive()) {
            throw badRequest("This file is not marked sensitive; no verification is required");
        }
        DriveMailService mail = requireMail();
        mail.sendOtp(emailOtp.generate(file.getId()), file.getOriginalFilename());
    }

    /** Sends a file to the owner's email (attachment if small, else a download link). */
    public void sendFileToEmail(UUID id, String otp) {
        DriveFile file = requireFile(id);
        DriveMailService mail = requireMail();
        assertSensitiveAccessAllowed(file, otp);
        byte[] plain = crypto.decrypt(storage.get(file.getStorageKey()), file.getEncIv(), file.getEncWrappedKey());
        mail.sendFile(file, plain);
    }

    /** Enforces the email-OTP second factor for sensitive files; a no-op for ordinary files. */
    private void assertSensitiveAccessAllowed(DriveFile file, String otp) {
        if (!file.isSensitive()) {
            return;
        }
        if (mailProvider.getIfAvailable() == null) {
            // Fail closed: a sensitive file can't be released without the email second factor.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This file is sensitive and requires email verification, which is not configured");
        }
        if (otp == null || otp.isBlank() || !emailOtp.verify(file.getId(), otp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "A valid verification code is required for this sensitive file");
        }
    }

    private DriveMailService requireMail() {
        DriveMailService mail = mailProvider.getIfAvailable();
        if (mail == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not configured");
        }
        return mail;
    }

    /**
     * Redeems a download token (single-use): validates and burns it, fetches the ciphertext,
     * decrypts it, and returns the plaintext with its original filename and content type. An
     * unknown / expired / already-used token is a 410 Gone.
     */
    public DownloadedFile download(String token) {
        UUID fileId = downloadTokens.redeem(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "Download link has expired or already been used"));
        DriveFile file = files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File no longer exists"));
        byte[] ciphertext = storage.get(file.getStorageKey());
        byte[] plain = crypto.decrypt(ciphertext, file.getEncIv(), file.getEncWrappedKey());
        return new DownloadedFile(plain, file.getOriginalFilename(), file.getContentType());
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private DriveFolder requireFolder(UUID id) {
        return folders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }

    private DriveFile requireFile(UUID id) {
        return files.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw badRequest("Folder name must not be blank");
        }
        return name.trim();
    }

    /** Rejects a duplicate name in the same parent (covers the root, which the DB UNIQUE cannot). */
    private void requireUniqueName(UUID parentId, String name, UUID excludeId) {
        boolean clash = (parentId == null)
                ? folders.findByParentIdIsNullOrderByNameAsc().stream()
                        .anyMatch(f -> f.getName().equalsIgnoreCase(name) && !f.getId().equals(excludeId))
                : folders.findByParentIdOrderByNameAsc(parentId).stream()
                        .anyMatch(f -> f.getName().equalsIgnoreCase(name) && !f.getId().equals(excludeId));
        if (clash) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A folder with that name already exists here");
        }
    }

    /** True if {@code candidate} is {@code rootId} or anywhere beneath it (walks up to the root). */
    private boolean isInSubtreeOf(UUID candidate, UUID rootId) {
        UUID cursor = candidate;
        while (cursor != null) {
            if (cursor.equals(rootId)) {
                return true;
            }
            cursor = folders.findById(cursor).map(DriveFolder::getParentId).orElse(null);
        }
        return false;
    }

    /** All folder ids in the subtree rooted at {@code rootId} (inclusive). */
    private Set<UUID> collectSubtreeIds(UUID rootId) {
        Set<UUID> ids = new LinkedHashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (ids.add(current)) {
                for (DriveFolder child : folders.findByParentIdOrderByNameAsc(current)) {
                    stack.push(child.getId());
                }
            }
        }
        return ids;
    }

    private void deleteObjectQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Drive: failed to delete object '{}' (orphaned in storage): {}", key, e.getMessage());
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    // ── Content-type magic-byte verification (mirrors ProfileController) ─────────

    private static void verifyMagicBytes(byte[] data, String contentType) {
        boolean ok = switch (contentType) {
            // Text-based formats have no reliable signature — accept as declared.
            case "text/plain", "text/csv", "text/markdown", "application/json" -> true;
            case "image/jpeg" -> startsWith(data, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> startsWith(data, 0x47, 0x49, 0x46, 0x38); // "GIF8"
            case "image/webp" -> startsWith(data, 0x52, 0x49, 0x46, 0x46)   // "RIFF"
                    && regionMatches(data, 8, 0x57, 0x45, 0x42, 0x50);      // "WEBP"
            case "application/pdf" -> startsWith(data, 0x25, 0x50, 0x44, 0x46); // "%PDF"
            // Legacy Office (.doc/.xls) are OLE compound files.
            case "application/msword", "application/vnd.ms-excel" ->
                    startsWith(data, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            // ZIP and the OOXML/zip-based formats (.docx/.xlsx) start with "PK\x03\x04".
            case "application/zip",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    startsWith(data, 0x50, 0x4B, 0x03, 0x04);
            default -> false;
        };
        if (!ok) {
            throw badRequest("File content does not match its declared type");
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

    /** Strips any directory components a browser may include so we persist a bare file name. */
    private static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return DEFAULT_FILENAME;
        }
        String name = original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.isEmpty() ? DEFAULT_FILENAME : name;
    }
}
