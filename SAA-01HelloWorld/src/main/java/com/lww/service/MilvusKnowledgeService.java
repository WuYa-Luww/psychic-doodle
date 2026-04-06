package com.lww.service;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus Knowledge Service using V2 API
 */
@Service
public class MilvusKnowledgeService {

    private final MilvusClientV2 milvusClient;
    private final String collectionName;

    public MilvusKnowledgeService(@Qualifier("milvusClientV2") MilvusClientV2 milvusClient,
                                   @Qualifier("milvusCollectionName") String collectionName) {
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
    }

    /**
     * Insert document chunk to vector store
     */
    public void insertDocument(String id, float[] vector, String content, String source) {
        // Build JsonObject for insertion (Milvus V2 SDK requires JsonObject)
        JsonObject data = new JsonObject();
        data.addProperty("id", id);

        JsonArray vectorArray = new JsonArray();
        for (float v : vector) {
            vectorArray.add(v);
        }
        data.add("vector", vectorArray);

        data.addProperty("content", content);
        data.addProperty("source", source);

        List<JsonObject> dataList = new ArrayList<>();
        dataList.add(data);

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(dataList)
                .build();

        milvusClient.insert(insertReq);
    }

    /**
     * Search similar content
     */
    public List<SearchResult> search(float[] queryVector, int topK, double minScore) {
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vectorList.add(v);
        }

        FloatVec floatVec = new FloatVec(vectorList);

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(floatVec))
                .topK(topK)
                .outputFields(Arrays.asList("id", "content", "source"))
                .build();

        SearchResp searchResp = milvusClient.search(searchReq);

        List<SearchResult> searchResults = new ArrayList<>();
        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();

        if (results != null && !results.isEmpty()) {
            for (SearchResp.SearchResult result : results.get(0)) {
                double score = result.getScore();
                if (score >= minScore) {
                    SearchResult sr = new SearchResult();
                    sr.setId(result.getId().toString());
                    sr.setScore(score);

                    Map<String, Object> entity = result.getEntity();
                    if (entity != null) {
                        sr.setContent(entity.get("content") != null ? entity.get("content").toString() : "");
                        sr.setSource(entity.get("source") != null ? entity.get("source").toString() : "");
                    }

                    searchResults.add(sr);
                }
            }
        }

        return searchResults;
    }

    public static class SearchResult {
        private String id;
        private String content;
        private String source;
        private Double score;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "id='" + id + '\'' +
                    ", content='" + content + '\'' +
                    ", source='" + source + '\'' +
                    ", score=" + score +
                    '}';
        }
    }
}
