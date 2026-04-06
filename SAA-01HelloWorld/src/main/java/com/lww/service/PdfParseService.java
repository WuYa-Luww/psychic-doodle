package com.lww.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfParseService {

    /**
     * Parse PDF content from file path and chunk it
     */
    public List<String> parsePdfToStrings(String filePath) throws IOException {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists()) {
            throw new IOException("PDF file not found: " + filePath);
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();

            // Extract text from all pages
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                fullText.append(pageText).append("\n");
            }

            return chunkText(fullText.toString(), 500, 100);
        }
    }

    /**
     * Chunk text: split by character count with overlap
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String cleanedText = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < cleanedText.length()) {
            int end = Math.min(start + chunkSize, cleanedText.length());
            String chunk = cleanedText.substring(start, end);

            // Try to break at sentence end
            if (end < cleanedText.length()) {
                int lastPeriod = Math.max(chunk.lastIndexOf("."), chunk.lastIndexOf("。"));
                if (lastPeriod > chunkSize / 2) {
                    chunk = chunk.substring(0, lastPeriod + 1);
                    end = start + lastPeriod + 1;
                }
            }

            chunks.add(chunk.trim());
            start += chunkSize - overlap;
        }

        return chunks;
    }
}
