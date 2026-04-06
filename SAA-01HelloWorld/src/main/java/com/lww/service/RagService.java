package com.lww.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RAG (Retrieval Augmented Generation) Service
 * Integrates EmbeddingModel + Milvus vector storage for knowledge base storage and retrieval
 */
@Service
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final MilvusEmbeddingStore embeddingStore;

    // Auto-increment ID counter for new documents
    private final AtomicLong idCounter = new AtomicLong(System.currentTimeMillis());

    public RagService(EmbeddingModel embeddingModel, MilvusEmbeddingStore embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Add document to knowledge base
     */
    public String addDocument(String id, String content, String source) {
        Metadata metadata = Metadata.metadata("id", id);
        metadata.put("source", source);

        TextSegment segment = TextSegment.from(content, metadata);

        Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
        Embedding embedding = embeddingResponse.content();

        embeddingStore.add(id, embedding);
        return id;
    }

    /**
     * Add document (auto generate ID)
     */
    public String addDocument(String content, String source) {
        Metadata metadata = Metadata.metadata("source", source);
        TextSegment segment = TextSegment.from(content, metadata);

        Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
        Embedding embedding = embeddingResponse.content();

        return embeddingStore.add(embedding, segment);
    }

    /**
     * Batch add documents
     */
    public List<String> addDocuments(List<String[]> documents) {
        List<TextSegment> segments = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (String[] doc : documents) {
            String content = doc[0];
            String source = doc.length > 1 ? doc[1] : "";
            long id = idCounter.getAndIncrement();

            Metadata metadata = Metadata.metadata("id", String.valueOf(id));
            metadata.put("source", source);

            TextSegment segment = TextSegment.from(content, metadata);
            segments.add(segment);
            ids.add(String.valueOf(id));
        }

        Response<List<Embedding>> embeddingResponse = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = embeddingResponse.content();

        return embeddingStore.addAll(embeddings, segments);
    }

    /**
     * Default minimum similarity score threshold
     * 余弦相似度下，0.7 是较好的过滤阈值
     */
    private static final double DEFAULT_MIN_SCORE = 0.7;

    /**
     * Semantic search in knowledge base
     */
    public List<SearchResult> search(String query, int topK, double minScore) {
        Response<Embedding> queryEmbeddingResponse = embeddingModel.embed(query);
        Embedding queryEmbedding = queryEmbeddingResponse.content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(minScore)
            .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        List<SearchResult> results = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
            SearchResult sr = new SearchResult();
            sr.setId(match.embeddingId());
            sr.setScore(match.score());
            sr.setContent(match.embedded().text());

            Metadata metadata = match.embedded().metadata();
            if (metadata != null) {
                String sourceValue = metadata.getString("source");
                sr.setSource(sourceValue != null ? sourceValue : "");
            }

            results.add(sr);
        }

        return results;
    }

    /**
     * Semantic search (default minScore 0.7)
     */
    public List<SearchResult> search(String query, int topK) {
        return search(query, topK, DEFAULT_MIN_SCORE);
    }

    /**
     * Get document count in knowledge base
     */
    public long getDocumentCount() {
        return embeddingStore.count();
    }

    /**
     * Delete document
     */
    public void deleteDocument(String id) {
        embeddingStore.delete(id);
    }

    /**
     * Build RAG context prompt
     */
    public String buildContext(String query, int topK) {
        // 构建上下文时使用稍低的阈值 0.5，确保能召回相关内容
        List<SearchResult> results = search(query, topK, 0.5);

        if (results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("[Reference Knowledge Base Content]\n");

        int index = 1;
        for (SearchResult result : results) {
            context.append(index++).append(". ");
            context.append(result.getContent());
            if (result.getSource() != null && !result.getSource().isEmpty()) {
                context.append(" (Source: ").append(result.getSource()).append(")");
            }
            context.append("\n");
        }

        context.append("[Please answer based on the above knowledge base content]\n");
        return context.toString();
    }

    /**
     * Search result wrapper class
     */
    public static class SearchResult {
        private String id;
        private double score;
        private String content;
        private String source;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "id='" + id + '\'' +
                    ", score=" + score +
                    ", content='" + (content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content) + '\'' +
                    ", source='" + source + '\'' +
                    '}';
        }
    }
}
