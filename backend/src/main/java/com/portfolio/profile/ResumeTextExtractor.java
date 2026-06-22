package com.portfolio.profile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Extracts plain text from an uploaded PDF resume (Phase F1) with Apache PDFBox, so the curated
 * text — never the raw bytes — can be chunked + embedded into the RAG corpus. Fails soft: a
 * scanned/image-only or malformed PDF yields {@link Optional#empty()} (the upload still succeeds;
 * the resume just isn't searchable). DOC/DOCX text extraction is out of scope for v1.
 */
@Component
public class ResumeTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractor.class);

    /** Cap extracted text so a pathological PDF can't bloat the corpus / a prompt. */
    private static final int MAX_CHARS = 50_000;

    public Optional<String> extractPdfText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            String trimmed = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
            return Optional.of(trimmed.strip());
        } catch (Exception e) {
            log.warn("[resume] PDF text extraction failed; resume will not be searchable: {}", e.toString());
            return Optional.empty();
        }
    }
}
