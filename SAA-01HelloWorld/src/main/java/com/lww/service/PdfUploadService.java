package com.lww.service;

import com.lww.kb.KnowledgeBaseService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PDF 上传处理服务
 * 整合 PDF 解析、文本切分、知识库存储
 */
@Service
public class PdfUploadService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final PdfParseService pdfParseService;
    private final KnowledgeBaseService knowledgeBaseService;

    public PdfUploadService(PdfParseService pdfParseService,
                            KnowledgeBaseService knowledgeBaseService) {
        this.pdfParseService = pdfParseService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 检查 PDF 文件魔数 (Magic Number)
     * 有效 PDF 文件以 %PDF 开头
     */
    private boolean isValidPdfMagicNumber(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        return bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46;
    }

    /**
     * 处理 PDF 文件：解析、切分、存入知识库
     *
     * @param pdfBytes PDF 文件字节数组
     * @param fileName 原始文件名
     * @return 处理结果
     */
    public PdfUploadResult processPdf(byte[] pdfBytes, String fileName) {
        PdfUploadResult result = new PdfUploadResult();
        result.setFileName(fileName);

        // 1. 安全校验
        if (pdfBytes == null || pdfBytes.length == 0) {
            result.setSuccess(false);
            result.setMessage("文件内容为空");
            return result;
        }

        if (pdfBytes.length > MAX_FILE_SIZE) {
            result.setSuccess(false);
            result.setMessage("文件大小超过限制 (最大 10MB)");
            return result;
        }

        if (!isValidPdfMagicNumber(pdfBytes)) {
            result.setSuccess(false);
            result.setMessage("文件格式无效，不是有效的 PDF 文件");
            return result;
        }

        try {
            // 2. 保存临时 PDF 文件
            Path tempDir = Paths.get(System.getProperty("user.dir"), "data", "temp");
            Files.createDirectories(tempDir);
            Path tempFile = tempDir.resolve(UUID.randomUUID() + ".pdf");
            Files.write(tempFile, pdfBytes);

            try {
                // 3. 解析 PDF 并切分
                List<String> chunks = pdfParseService.parsePdfToStrings(tempFile.toString());
                result.setTotalChunks(chunks.size());

                // 4. 存入知识库 (使用结构化返回)
                List<String> kbIds = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    String chunk = chunks.get(i);
                    KnowledgeBaseService.PutResult putResult = knowledgeBaseService.putResult(
                            null,
                            fileName + " (片段" + (i + 1) + ")",
                            chunk
                    );
                    if (putResult.success) {
                        kbIds.add(putResult.kbId);
                    }
                }

                result.setSuccess(true);
                result.setMessage("成功处理 PDF，共 " + chunks.size() + " 个片段，已存入知识库");
                result.setChunkIds(kbIds);

            } finally {
                // 5. 删除临时文件
                Files.deleteIfExists(tempFile);
            }

        } catch (IOException e) {
            result.setSuccess(false);
            result.setMessage("PDF 解析失败: " + e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("处理失败: " + e.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * 获取示例 PDF 文件路径
     */
    public Path getSamplePdfPath() {
        return Paths.get(System.getProperty("user.dir"), "data", "sample.pdf");
    }

    /**
     * 检查示例 PDF 是否存在
     */
    public boolean samplePdfExists() {
        return Files.exists(getSamplePdfPath());
    }

    /**
     * PDF 上传结果
     */
    public static class PdfUploadResult {
        private boolean success;
        private String message;
        private String fileName;
        private int totalChunks;
        private List<String> chunkIds;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public List<String> getChunkIds() { return chunkIds; }
        public void setChunkIds(List<String> chunkIds) { this.chunkIds = chunkIds; }
    }
}
