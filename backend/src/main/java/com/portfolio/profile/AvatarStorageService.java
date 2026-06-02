package com.portfolio.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class AvatarStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private final Path uploadDir;

    public AvatarStorageService(@Value("${UPLOAD_DIR:uploads}") String dir) throws IOException {
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    /** Stores the file as {@code avatar.<ext>}, replacing any previous avatar. Returns the public URL path. */
    public String store(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, GIF or WebP images are allowed");
        }
        String ext = switch (contentType) {
            case "image/png"  -> "png";
            case "image/gif"  -> "gif";
            case "image/webp" -> "webp";
            default           -> "jpg";
        };
        deleteExistingAvatars();
        String filename = "avatar." + ext;
        Files.copy(file.getInputStream(), uploadDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + filename;
    }

    private void deleteExistingAvatars() throws IOException {
        try (Stream<Path> files = Files.list(uploadDir)) {
            files.filter(p -> p.getFileName().toString().startsWith("avatar."))
                 .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }
}
