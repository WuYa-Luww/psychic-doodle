package com.lww.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.QueryResp;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LangChain4j EmbeddingStore implementation for Milvus using V2 API
 * Compatible with ZhipuAI embedding model (1024 dimensions)
 */
@Service
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final MilvusClientV2 milvusClient;
    private final String collectionName;

    // Auto-increment ID counter for new documents
    private final AtomicLong idCounter = new AtomicLong(System.currentTimeMillis());

    public MilvusEmbeddingStore(@Qualifier("milvusClientV2") MilvusClientV2 milvusClient,
                                 @Qualifier("milvusCollectionName") String collectionName) {
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
    }

    @Override
    public String add(Embedding embedding) {
        long id = idCounter.getAndIncrement();
        addInternal(id, embedding, null);
        return String.valueOf(id);
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(Long.parseLong(id), embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        long id = idCounter.getAndIncrement();
        addInternal(id, embedding, textSegment);
        return String.valueOf(id);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>();
        for (Embedding embedding : embeddings) {
            ids.add(add(embedding));
        }
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            long id = idCounter.getAndIncrement();
            TextSegment segment = (textSegments != null && i < textSegments.size()) ? textSegments.get(i) : null;
            addInternal(id, embeddings.get(i), segment);
            ids.add(String.valueOf(id));
        }
        return ids;
    }

    private void addInternal(long id, Embedding embedding, TextSegment textSegment) {
        String text = textSegment != null ? textSegment.text() : "";

        // Build JsonObject for insertion (Milvus V2 SDK requires JsonObject)
        // Schema: id int64, vector floatVector, text Varchar(512)
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.add("vector", toJsonArray(embedding));
        data.addProperty("text", text);

        List<JsonObject> dataList = new ArrayList<>();
        dataList.add(data);

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(dataList)
                .build();

        milvusClient.insert(insertReq);
    }

    private com.google.gson.JsonArray toJsonArray(Embedding embedding) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        float[] vector = embedding.vector();
        for (float v : vector) {
            array.add(v);
        }
        return array;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        FloatVec queryVector = new FloatVec(toFloatList(request.queryEmbedding()));

        System.out.println("[DEBUG] Searching collection: " + collectionName);
        System.out.println("[DEBUG] Query vector dimension: " + request.queryEmbedding().vector().length);

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(queryVector))
                .topK(request.maxResults())
                .outputFields(Arrays.asList("id", "text", "vector"))
                .build();

        SearchResp searchResp = milvusClient.search(searchReq);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        double minScoreThreshold = request.minScore();

        System.out.println("[DEBUG] Search results: " + (results != null ? results.size() : "null"));
        System.out.println("[DEBUG] Min score threshold: " + minScoreThreshold);

        if (results != null && !results.isEmpty()) {
            System.out.println("[DEBUG] First result set size: " + results.get(0).size());
            for (SearchResp.SearchResult result : results.get(0)) {
                double score = result.getScore();
                System.out.println("[DEBUG] Result score: " + score + ", id: " + result.getId());

                if (score < minScoreThreshold) {
                    System.out.println("[DEBUG] Skipped due to low score");
                    continue;
                }

                String id = result.getId().toString();
                Map<String, Object> entity = result.getEntity();

                String content = "";
                float[] vector = null;
                if (entity != null) {
                    // Read from "text" field (user's schema)
                    if (entity.get("text") != null) {
                        content = entity.get("text").toString();
                        System.out.println("[DEBUG] Text content length: " + content.length());
                    }
                    if (entity.get("vector") != null) {
                        Object vectorObj = entity.get("vector");
                        if (vectorObj instanceof List) {
                            List<?> vectorList = (List<?>) vectorObj;
                            vector = new float[vectorList.size()];
                            for (int i = 0; i < vectorList.size(); i++) {
                                vector[i] = ((Number) vectorList.get(i)).floatValue();
                            }
                            System.out.println("[DEBUG] Vector dimension from DB: " + vector.length);
                        }
                    }
                }

                Metadata metadata = Metadata.metadata("id", id);

                TextSegment segment = TextSegment.from(content, metadata);

                // EmbeddingMatch requires 4 parameters: score, embeddingId, embedding, embedded
                Embedding embedding = vector != null ? new Embedding(vector) : null;
                matches.add(new EmbeddingMatch<>(score, id, embedding, segment));
            }
        }

        System.out.println("[DEBUG] Final matches count: " + matches.size());
        return new EmbeddingSearchResult<>(matches);
    }

    private List<Float> toFloatList(Embedding embedding) {
        float[] vector = embedding.vector();
        List<Float> floatList = new ArrayList<>(vector.length);
        for (float v : vector) {
            floatList.add(v);
        }
        return floatList;
    }

    public void delete(String id) {
        // id is int64, use numeric expression
        String expr = "id == " + id;

        io.milvus.v2.service.vector.request.DeleteReq deleteReq =
            io.milvus.v2.service.vector.request.DeleteReq.builder()
                .collectionName(collectionName)
                .filter(expr)
                .build();

        milvusClient.delete(deleteReq);
    }

    public void deleteAll(List<String> ids) {
        for (String id : ids) {
            delete(id);
        }
    }

    public long count() {
        try {
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("")
                    .outputFields(Collections.singletonList("count(*)"))
                    .build();

            QueryResp queryResp = milvusClient.query(queryReq);
            List<QueryResp.QueryResult> results = queryResp.getQueryResults();

            if (results != null && !results.isEmpty()) {
                Map<String, Object> entity = results.get(0).getEntity();
                if (entity != null && entity.get("count(*)") != null) {
                    return ((Number) entity.get("count(*)")).longValue();
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return 0;
    }
}
