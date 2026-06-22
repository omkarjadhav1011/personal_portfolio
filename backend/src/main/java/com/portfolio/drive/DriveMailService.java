package com.portfolio.drive;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends vault files and verification codes to the owner's fixed address ({@code DRIVE_NOTIFY_EMAIL})
 * over SMTP. A "send to my email" is a convenience, not a security control — the destination is the
 * owner's own inbox and login is the boundary.
 *
 * <p>Wired only when both {@code STORAGE_ENDPOINT} and {@code MAIL_HOST} are set, so the drive runs
 * fine without email configured (the email endpoints then report 503). Small files are attached;
 * larger ones go out as a fresh single-use {@link DownloadTokenService} link.
 */
@Service
@ConditionalOnProperty(name = {"STORAGE_ENDPOINT", "MAIL_HOST"})
public class DriveMailService {

    private static final Logger log = LoggerFactory.getLogger(DriveMailService.class);
    /** Files at or below this size are attached; larger ones are sent as a download link. */
    private static final long ATTACH_LIMIT_BYTES = 20L * 1024 * 1024;

    private final JavaMailSender mailSender;
    private final DownloadTokenService downloadTokens;
    private final String from;
    private final String to;
    private final String publicBaseUrl;

    public DriveMailService(JavaMailSender mailSender,
                            DownloadTokenService downloadTokens,
                            @Value("${MAIL_FROM:${MAIL_USERNAME:}}") String from,
                            @Value("${DRIVE_NOTIFY_EMAIL:}") String to,
                            @Value("${DRIVE_PUBLIC_BASE_URL:http://localhost:8081}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.downloadTokens = downloadTokens;
        this.from = from;
        this.to = to;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Sends {@code file}: attached when small, otherwise as a fresh single-use download link. */
    public void sendFile(DriveFile file, byte[] plaintext) {
        requireRecipient();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applyFromTo(helper);
            String name = file.getOriginalFilename();
            if (plaintext.length <= ATTACH_LIMIT_BYTES) {
                helper.setSubject("[Vault] " + name);
                helper.setText("Your file \"" + name + "\" is attached.");
                helper.addAttachment(name, new ByteArrayResource(plaintext), file.getContentType());
            } else {
                String token = downloadTokens.issue(file.getId());
                String link = publicBaseUrl + "/api/drive/download/" + token;
                helper.setSubject("[Vault] Download link for " + name);
                helper.setText("Your file \"" + name + "\" is too large to attach.\n\n"
                        + "Download it here (valid 5 minutes, single use):\n" + link);
            }
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Drive: failed to send file email: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to send the email");
        }
    }

    /** Emails a verification code for a sensitive file. */
    public void sendOtp(String code, String filename) {
        requireRecipient();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applyFromTo(helper);
            helper.setSubject("[Vault] Your verification code");
            helper.setText("Verification code for \"" + filename + "\": " + code
                    + "\n\nThis code expires in 10 minutes.");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Drive: failed to send OTP email: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to send the verification email");
        }
    }

    private void requireRecipient() {
        if (to == null || to.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DRIVE_NOTIFY_EMAIL is not configured");
        }
    }

    private void applyFromTo(MimeMessageHelper helper) throws Exception {
        helper.setFrom(from != null && !from.isBlank() ? from : to);
        helper.setTo(to);
    }
}
