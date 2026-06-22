package com.portfolio.drive;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * S3-compatible object-storage client for the Secure Document Vault ("Drive").
 *
 * <p>Wired ONLY when {@code STORAGE_ENDPOINT} is set — mirroring how OAuth2 login is wired only
 * when a provider is configured ({@code OAuth2ClientConfig}). This keeps the drive subsystem
 * optional: the app (and the test context, which sets no storage env) boots without ever
 * touching MinIO. Set the env vars (see {@code application.yml}) to enable it.
 *
 * <p>Path-style access is enabled because MinIO addresses buckets in the URL path
 * ({@code host/bucket}) rather than as a virtual host ({@code bucket.host}); AWS S3 and
 * Cloudflare R2 also accept path-style, so the same client works in prod via endpoint override.
 */
@Configuration
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class DriveStorageConfig {

    @Bean
    S3Client s3Client(
            @Value("${STORAGE_ENDPOINT}") String endpoint,
            @Value("${STORAGE_REGION:us-east-1}") String region,
            @Value("${STORAGE_ACCESS_KEY}") String accessKey,
            @Value("${STORAGE_SECRET_KEY}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                // JDK HttpURLConnection transport — no Apache/Netty, so no
                // httpclient5 version clash with Spring Boot's managed version.
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
