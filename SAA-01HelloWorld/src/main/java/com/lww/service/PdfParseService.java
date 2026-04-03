package com.lww.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextExtractor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfParseService {

    /**
     * 从文件路径解析 PDF 内容并切片
     */
    public List<String> parsePdfToStrings(String filePath) throws IOException {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists()) {
            throw new IOException("PDF file not found: " + filePath);
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);
            PDFTextExtractor textExtractor = new PDFTextExtractor(document);
            StringBuilder fullText = new StringBuilder();

            // 提取所有页面文本
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                String pageText = textExtractor.getTextFromPage(page);
                fullText.append(pageText).append("\n");
            }

            return chunkText(fullText.toString(), 500, 100); // 每片 500 字，重叠 100 字
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    /**
     * 文本切片：按字符数切割，保留重叠
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String cleanedText = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < cleanedText.length()) {
            int end = Math.min(start + chunkSize, cleanedText.length());
            String chunk = cleanedText.substring(start, end);

            // 尽量在句号处切断
            if (end < cleanedText.length()) {
                int lastPeriod = chunk.lastIndexOf(".");
                int lastEnPeriod = chunk.lastIndexOf(".");
                int bestBreak = Math.max(lastPeriod, lastEnPeriod);
                if (bestBreak > chunkSize / 2) {
                    chunk = chunk.substring(0, bestBreak + 1);
                    end = start + bestBreak + 1;
                }
            }

            chunks.add(chunk.trim());
            start += chunkSize - overlap;
        }

        return chunks;
    }
}
