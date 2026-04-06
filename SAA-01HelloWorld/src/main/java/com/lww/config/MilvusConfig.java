package com.lww.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.index.request.CreateIndexReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus Vector Database Configuration
 * Using Milvus Java SDK 2.5 V2 API
 */
@Configuration
public class MilvusConfig {

    @Value("${milvus.endpoint:127.0.0.1:19530}")
    private String milvusEndpoint;

    @Value("${milvus.database:default}")
    private String databaseName;

    @Value("${milvus.collection:latitude15}")
    private String collectionName;

    /**
     * ZhipuAI Embedding model output dimension: 1024
     */
    private static final int EMBEDDING_DIMENSION = 1024;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri("http://" + milvusEndpoint)
                .dbName(databaseName)
                .build();
        return new MilvusClientV2(connectConfig);
    }

    @Bean
    public String milvusCollectionName() {
        return collectionName;
    }

    @Bean
    public int embeddingDimension() {
        return EMBEDDING_DIMENSION;
    }

    /**
     * Initialize Milvus collection on startup
     */
    @Bean
    public boolean initMilvusCollection(MilvusClientV2 client) {
        try {
            // Check if collection exists
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();

            Boolean hasCollection = client.hasCollection(hasCollectionReq);

            if (hasCollection == null || !hasCollection) {
                createCollection(client, collectionName, EMBEDDING_DIMENSION);
            } else {
                System.out.println("Milvus collection '" + collectionName + "' already exists");
            }

            // Load collection into memory (required for search)
            loadCollection(client, collectionName);

            return true;
        } catch (Exception e) {
            System.err.println("Failed to initialize Milvus collection: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void loadCollection(MilvusClientV2 client, String collectionName) {
        LoadCollectionReq loadCollectionReq = LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build();
        client.loadCollection(loadCollectionReq);
        System.out.println("Milvus collection '" + collectionName + "' loaded into memory");
    }

    private void createCollection(MilvusClientV2 client, String collectionName, int dimension) {
        // Create collection schema
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .build();

        // Add ID field (VarChar as primary key)
        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .autoID(false)
                .maxLength(64)
                .build());

        // Add vector field
        schema.addField(AddFieldReq.builder()
                .fieldName("vector")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());

        // Add content field
        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());

        // Add source field
        schema.addField(AddFieldReq.builder()
                .fieldName("source")
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());

        // Create collection
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .build();

        client.createCollection(createCollectionReq);

        // Create index on vector field
        // 使用 COSINE 余弦相似度，分数范围 0-1，更直观
        IndexParam indexParam = IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.IVF_FLAT)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(java.util.Map.of("nlist", 1024))
                .build();

        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(java.util.Collections.singletonList(indexParam))
                .build();

        client.createIndex(createIndexReq);

        System.out.println("Milvus collection '" + collectionName + "' created successfully with " + dimension + " dimensions");
    }
}
