package com.portfolio.profile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsTextFromRealPdf() throws Exception {
        byte[] pdf = pdfWithLines(List.of(
                "John Doe - Senior Java Engineer",
                "5 years building Spring Boot services and PostgreSQL backends."));

        Optional<String> text = extractor.extractPdfText(pdf);
        assertTrue(text.isPresent(), "text extracted from a real PDF");
        assertTrue(text.get().contains("Senior Java Engineer"));
        assertTrue(text.get().contains("Spring Boot"));
    }

    @Test
    void returnsEmptyForNonPdfBytes() {
        assertTrue(extractor.extractPdfText("this is not a pdf".getBytes()).isEmpty());
        assertFalse(extractor.extractPdfText(new byte[]{0x25, 0x50, 0x44, 0x46, 0x00}).isPresent(),
                "a truncated/garbage PDF fails soft, not loud");
    }

    private static byte[] pdfWithLines(List<String> lines) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
