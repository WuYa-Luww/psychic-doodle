package com.lww.controller;

import com.lww.service.RagService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 知识库管理接口
 */
@RestController
@RequestMapping("/api/kb")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 添加文档到知识库
     */
    @PostMapping("/add")
    public String addDocument(@RequestBody DocumentRequest request) {
        try {
            System.out.println("Adding document: content=" + request.getContent() + ", source=" + request.getSource());
            String id = ragService.addDocument(request.getContent(), request.getSource());
            System.out.println("Document added successfully with id: " + id);
            return "添加成功，文档ID: " + id;
        } catch (Exception e) {
            System.err.println("添加失败: " + e.getMessage());
            e.printStackTrace();
            return "添加失败: " + e.getMessage();
        }
    }

    /**
     * 批量添加文档
     */
    @PostMapping("/batch")
    public String addDocuments(@RequestBody List<DocumentRequest> documents) {
        try {
            List<String[]> docs = documents.stream()
                .map(d -> new String[]{d.getContent(), d.getSource() != null ? d.getSource() : ""})
                .toList();

            List<String> ids = ragService.addDocuments(docs);
            return "批量添加成功，共 " + ids.size() + " 条文档";
        } catch (Exception e) {
            return "批量添加失败: " + e.getMessage();
        }
    }

    /**
     * 搜索知识库
     * minScore相关度
     */
    @GetMapping("/search")
    public List<RagService.SearchResult> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.5") double minScore) {
        return ragService.search(query, topK, minScore);
    }

    /**
     * 获取 RAG 上下文（用于构建 Prompt）
     */
    @GetMapping("/context")
    public String getContext(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK) {
        return ragService.buildContext(query, topK);
    }

    /**
     * 获取知识库统计
     */
    @GetMapping("/stats")
    public String getStats() {
        long count = ragService.getDocumentCount();
        return "知识库当前文档数量: " + count;
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    public String deleteDocument(@PathVariable String id) {
        try {
            ragService.deleteDocument(id);
            return "删除成功";
        } catch (Exception e) {
            return "删除失败: " + e.getMessage();
        }
    }

    /**
     * 文档请求 DTO
     */
    public static class DocumentRequest {
        private String id;
        private String content;
        private String source;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
