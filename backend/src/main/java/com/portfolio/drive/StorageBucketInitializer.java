package com.portfolio.drive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Ensures the drive storage bucket exists at startup: {@code headBucket} → create if missing.
 *
 * <p>Gated by {@code STORAGE_ENDPOINT} (same condition as {@link DriveStorageConfig}), so it only
 * runs when the drive subsystem is wired. <strong>Fail-soft:</strong> if MinIO/S3 is unreachable
 * at boot, it logs an error and lets the app start anyway — the document vault is an additive
 * subsystem and must never take down the rest of the portfolio. The upload/download endpoints
 * surface storage errors per-request instead.
 */
@Component
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class StorageBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageBucketInitializer.class);

    private final S3Client s3;
    private final String bucket;

    public StorageBucketInitializer(S3Client s3, @Value("${STORAGE_BUCKET:portfolio-drive}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.headBucket(b -> b.bucket(bucket));
            log.info("Drive storage bucket '{}' already exists", bucket);
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (S3Exception e) {
            // Some S3-compatible servers return a bare 404 instead of the typed exception.
            if (e.statusCode() == 404) {
                createBucket();
            } else {
                log.error("Drive storage: error verifying bucket '{}' (HTTP {}): {}",
                        bucket, e.statusCode(), e.awsErrorDetails().errorMessage());
            }
        } catch (SdkException e) {
            log.error("Drive storage unreachable at startup; bucket '{}' not verified. "
                    + "Drive endpoints will fail until storage is reachable. Cause: {}",
                    bucket, e.getMessage());
        }
    }

    private void createBucket() {
        try {
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Drive storage bucket '{}' created", bucket);
        } catch (SdkException e) {
            log.error("Drive storage: failed to create bucket '{}': {}", bucket, e.getMessage());
        }
    }
}
