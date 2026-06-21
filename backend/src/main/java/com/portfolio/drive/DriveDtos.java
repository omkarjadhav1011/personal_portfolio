package com.portfolio.drive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request/response shapes for the drive API. Response DTOs deliberately expose only safe metadata —
 * never {@code storageKey}, {@code encIv} or {@code encWrappedKey}.
 */
public final class DriveDtos {

    private DriveDtos() {
    }

    public record FolderDto(UUID id, UUID parentId, String name, Instant createdAt) {
        static FolderDto from(DriveFolder f) {
            return new FolderDto(f.getId(), f.getParentId(), f.getName(), f.getCreatedAt());
        }
    }

    public record FileDto(UUID id, UUID folderId, String filename, String contentType,
                          long sizeBytes, boolean sensitive, Instant createdAt) {
        static FileDto from(DriveFile f) {
            return new FileDto(f.getId(), f.getFolderId(), f.getOriginalFilename(),
                    f.getContentType(), f.getSizeBytes(), f.isSensitive(), f.getCreatedAt());
        }
    }

    /** Contents of a folder, or of the vault root when {@code folder} is {@code null}. */
    public record FolderContentsDto(FolderDto folder, List<FolderDto> folders, List<FileDto> files) {
    }

    /** Response of the download-token endpoint: the single-use token + how long it stays valid. */
    public record DownloadTokenResponse(String token, long expiresInSeconds) {
    }

    public record CreateFolderRequest(@NotBlank @Size(max = 255) String name, UUID parentId) {
    }

    /**
     * Rename and/or move. Treated as desired end-state: {@code name} (when non-blank) becomes the
     * new name; {@code parentId} becomes the new parent ({@code null} = root). The frontend echoes
     * the folder's current parentId on a pure rename so it does not get moved.
     */
    public record UpdateFolderRequest(@Size(max = 255) String name, UUID parentId) {
    }
}
