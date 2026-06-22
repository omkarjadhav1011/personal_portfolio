package com.portfolio.drive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Thin boundary over the S3-compatible object store (MinIO / R2 / S3). It only ever moves
 * <strong>ciphertext</strong> — encryption/decryption happens in {@link EnvelopeCryptoService}
 * before/after — so the stored object's content type is opaque ({@code application/octet-stream}).
 * Gated on {@code STORAGE_ENDPOINT}, like the rest of the drive subsystem.
 */
@Service
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class StorageService {

    private static final String CIPHERTEXT_CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3;
    private final String bucket;

    public StorageService(S3Client s3, @Value("${STORAGE_BUCKET:portfolio-drive}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Stores ciphertext under {@code key} (overwrites if present). */
    public void put(String key, byte[] ciphertext) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(CIPHERTEXT_CONTENT_TYPE)
                        .contentLength((long) ciphertext.length)
                        .build(),
                RequestBody.fromBytes(ciphertext));
    }

    /** Returns the ciphertext stored under {@code key}. */
    public byte[] get(String key) {
        ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
        return object.asByteArray();
    }

    /** Deletes the object. Idempotent — S3 reports success even when the key is already absent. */
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
