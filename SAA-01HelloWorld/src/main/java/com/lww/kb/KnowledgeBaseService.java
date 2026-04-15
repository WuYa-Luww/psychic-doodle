package com.lww.kb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 简易“可写可读”知识库（本地持久化）。
 * 写入：更新 items + 重建向量库并落盘。
 * 读取：通过 embedding 相似度检索返回最相关片段。
 */
public class KnowledgeBaseService {

    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path baseDir;
    private final Path itemsFile;
    private final Path embeddingFile;

    private final Object lock = new Object();

    private final Map<String, KnowledgeItem> items = new HashMap<>();
    private InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    public KnowledgeBaseService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.baseDir = Paths.get(System.getProperty("user.dir"), "data", "kb");
        this.itemsFile = baseDir.resolve("kb-items.json");
        this.embeddingFile = baseDir.resolve("kb-embeddings.json");
        init();
    }

    private void init() {
        try {
            Files.createDirectories(baseDir);

            if (Files.exists(itemsFile)) {
                Map<String, KnowledgeItem> loaded = objectMapper.readValue(
                        itemsFile.toFile(),
                        new TypeReference<Map<String, KnowledgeItem>>() {}
                );
                items.clear();
                items.putAll(loaded);
            }

            // 启动时以 items 为准重建向量库，保证一致性
            rebuildEmbeddingStoreAndPersist();
        } catch (Exception e) {
            // 失败时保持空知识库，不阻断服务启动
            items.clear();
            embeddingStore = new InMemoryEmbeddingStore<>();
        }
    }

    public String put(String kbId, String title, String content) {
        if (content == null || content.trim().isEmpty()) {
            return "kb_put 失败：content 不能为空";
        }

        String id = (kbId == null || kbId.trim().isEmpty()) ? UUID.randomUUID().toString() : kbId.trim();
        String t = title == null ? "" : title.trim();
        String c = content.trim();

        synchronized (lock) {
            items.put(id, new KnowledgeItem(id, t, c, Instant.now().toString()));
            try {
                persistItems();
                rebuildEmbeddingStoreAndPersist();
            } catch (Exception e) {
                return "kb_put 写入失败：" + e.getMessage();
            }
        }

        return "kb_put 成功：kbId=" + id;
    }

    public String get(String kbId) {
        if (kbId == null || kbId.trim().isEmpty()) {
            return "kb_get 失败：kbId 不能为空";
        }

        KnowledgeItem item = items.get(kbId.trim());
        if (item == null) {
            return "kb_get 未找到：kbId=" + kbId;
        }

        return "kb_get 命中：kbId=" + item.id + ", title=" + item.title + "\ncontent:\n" + item.content;
    }

    public String search(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return "kb_search 失败：query 不能为空";
        }
        if (topK <= 0) topK = 5;
        topK = Math.min(10, topK);

        InMemoryEmbeddingStore<TextSegment> storeSnapshot = this.embeddingStore;

        try {
            List<TextSegment> qSegments = List.of(TextSegment.from(query.trim()));
            Response<List<Embedding>> qEmbeddings = embeddingModel.embedAll(qSegments);
            List<Embedding> embeddings = qEmbeddings.content();
            if (embeddings == null || embeddings.isEmpty()) {
                return "kb_search 出错：query embedding 为空";
            }

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddings.get(0))
                    .maxResults(topK)
                    .minScore(0.0)
                    .build();

            EmbeddingSearchResult<TextSegment> result = storeSnapshot.search(request);
            if (result.matches() == null || result.matches().isEmpty()) {
                return "kb_search 无结果";
            }

            List<String> lines = new ArrayList<>();
            for (dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment> match : result.matches()) {
                TextSegment seg = match.embedded();
                Metadata meta = seg.metadata();
                String id = meta == null ? null : meta.getString("kbId");
                String title = meta == null ? null : meta.getString("title");

                String snippet = seg.text();
                if (snippet != null && snippet.length() > 500) {
                    snippet = snippet.substring(0, 500) + "...";
                }

                lines.add("score=" + String.format("%.4f", match.score())
                        + ", kbId=" + id
                        + ", title=" + title
                        + ", snippet=" + snippet);
            }
            return "kb_search topK结果：\n" + String.join("\n", lines);
        } catch (Exception e) {
            return "kb_search 出错：" + e.getMessage();
        }
    }

    private void persistItems() throws Exception {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(itemsFile.toFile(), items);
    }

    private void rebuildEmbeddingStoreAndPersist() throws Exception {
        InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();

        if (!items.isEmpty()) {
            List<TextSegment> segments = new ArrayList<>();
            for (KnowledgeItem item : items.values()) {
                Metadata meta = Metadata.metadata("kbId", item.id);
                meta = meta.put("title", item.title);
                segments.add(TextSegment.textSegment(item.content, meta));
            }

            // 批量 embedding 并写入向量库
            Response<List<Embedding>> embedded = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = embedded.content();

            for (int i = 0; i < segments.size(); i++) {
                newStore.add(embeddings.get(i), segments.get(i));
            }
        }

        this.embeddingStore = newStore;
        embeddingStore.serializeToFile(embeddingFile);
    }

    public static class KnowledgeItem {
        public String id;
        public String title;
        public String content;
        public String updatedAt;

        public KnowledgeItem() {
        }

        public KnowledgeItem(String id, String title, String content, String updatedAt) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.updatedAt = updatedAt;
        }
    }

    /**
     * 结构化返回结果，避免字符串解析
     */
    public static class PutResult {
        public final boolean success;
        public final String kbId;
        public final String errorMessage;

        private PutResult(boolean success, String kbId, String errorMessage) {
            this.success = success;
            this.kbId = kbId;
            this.errorMessage = errorMessage;
        }

        public static PutResult ok(String kbId) {
            return new PutResult(true, kbId, null);
        }

        public static PutResult fail(String message) {
            return new PutResult(false, null, message);
        }
    }

    /**
     * 结构化存储方法，返回包含成功状态和 ID 的结果对象
     */
    public PutResult putResult(String kbId, String title, String content) {
        if (content == null || content.trim().isEmpty()) {
            return PutResult.fail("content 不能为空");
        }

        String id = (kbId == null || kbId.trim().isEmpty()) ? UUID.randomUUID().toString() : kbId.trim();
        String t = title == null ? "" : title.trim();
        String c = content.trim();

        synchronized (lock) {
            items.put(id, new KnowledgeItem(id, t, c, Instant.now().toString()));
            try {
                persistItems();
                rebuildEmbeddingStoreAndPersist();
            } catch (Exception e) {
                return PutResult.fail("写入失败: " + e.getMessage());
            }
        }

        return PutResult.ok(id);
    }
}

