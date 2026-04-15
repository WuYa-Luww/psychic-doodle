package com.lww.controller;

import com.lww.service.PdfUploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 上传控制器
 * 提供 PDF 文件上传、示例下载功能
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfUploadController {

    private final PdfUploadService pdfUploadService;

    public PdfUploadController(PdfUploadService pdfUploadService) {
        this.pdfUploadService = pdfUploadService;
    }

    /**
     * 下载示例 PDF
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            Path pdfPath = pdfUploadService.getSamplePdfPath();

            if (!Files.exists(pdfPath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] pdfBytes = Files.readAllBytes(pdfPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "医疗知识示例.pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 上传并处理 PDF
     */
    @PostMapping("/upload")
    public PdfUploadService.PdfUploadResult uploadPdf(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            PdfUploadService.PdfUploadResult result = new PdfUploadService.PdfUploadResult();
            result.setSuccess(false);
            result.setMessage("请选择 PDF 文件");
            return result;
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            PdfUploadService.PdfUploadResult result = new PdfUploadService.PdfUploadResult();
            result.setSuccess(false);
            result.setMessage("仅支持 PDF 格式文件");
            return result;
        }

        try {
            byte[] pdfBytes = file.getBytes();
            return pdfUploadService.processPdf(pdfBytes, filename);
        } catch (Exception e) {
            PdfUploadService.PdfUploadResult result = new PdfUploadService.PdfUploadResult();
            result.setSuccess(false);
            result.setMessage("处理失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 检查示例 PDF 状态
     */
    @GetMapping("/status")
    public Object getStatus() {
        return java.util.Map.of(
                "sampleExists", pdfUploadService.samplePdfExists()
        );
    }
}
