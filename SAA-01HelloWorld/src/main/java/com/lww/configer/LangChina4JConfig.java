package com.lww.configer;

import com.lww.controller.AgentController;
import com.lww.kb.KnowledgeBaseService;
import com.lww.service.MilvusEmbeddingStore;
import com.lww.service.RagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class LangChina4JConfig {

    @Value("${spring.ai.zhipuai.api-key}")
    private String zhipuApiKey;

    private Duration timeout = Duration.ofSeconds(60);

    /**
     * 同步聊天模型 - 用于需要工具调用的同步场景
     * 注意：glm-4-flash 不支持函数调用，使用 glm-4 支持工具调用
     */
    @Bean
    public ChatLanguageModel dashscopeChatLanguageModel() {
        return dev.langchain4j.model.zhipu.ZhipuAiChatModel.builder()
                .apiKey(zhipuApiKey)
                .model("glm-4")  // glm-4 支持函数调用，glm-4-flash 不支持
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .build();
    }

    /**
     * 流式聊天模型 - 用于 SSE 流式输出
     * 注意：glm-4-flash 不支持函数调用，使用 glm-4 支持工具调用
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return dev.langchain4j.model.zhipu.ZhipuAiStreamingChatModel.builder()
                .apiKey(zhipuApiKey)
                .model("glm-4")  // glm-4 支持函数调用，glm-4-flash 不支持
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .build();
    }

    @Bean
    public EmbeddingModel kbEmbeddingModel() {
        return dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel.builder()
                .apiKey(zhipuApiKey)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .build();
    }

    @Bean
    public RagService ragService(EmbeddingModel kbEmbeddingModel, MilvusEmbeddingStore milvusEmbeddingStore) {
        return new RagService(kbEmbeddingModel, milvusEmbeddingStore);
    }

    @Bean
    public KnowledgeBaseService knowledgeBaseService(@Qualifier("kbEmbeddingModel") EmbeddingModel embeddingModel) {
        return new KnowledgeBaseService(embeddingModel);
    }

    @Bean
    public AgentController.AgentAssistant agentAssistant(ChatLanguageModel dashscopeChatLanguageModel,
                                                          RagService ragService) {
        return AiServices.builder(AgentController.AgentAssistant.class)
                .chatLanguageModel(dashscopeChatLanguageModel)
                .tools(new AgentTools(ragService))
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    static class AgentTools {

        private final RagService ragService;

        AgentTools(RagService ragService) {
            this.ragService = ragService;
        }

        @Tool("Get current server time in UTC ISO-8601 format")
        public String nowUtcIso() {
            return Instant.now().toString();
        }

        @Tool("Convert UTC epoch milliseconds to ISO-8601 format string")
        public String epochMillisToUtcIso(@P("UTC epoch milliseconds timestamp") long epochMillis) {
            return Instant.ofEpochMilli(epochMillis).toString();
        }

        @Tool("Write to knowledge base: kbId(unique id), content, source. Returns write result")
        public String kb_put(@P("kbId") String kbId,
                             @P("content") String content,
                             @P("source") String source) {
            try {
                String id = ragService.addDocument(kbId, content, source != null ? source : "user-input");
                return "kb_put success: kbId=" + id;
            } catch (Exception e) {
                return "kb_put failed: " + e.getMessage();
            }
        }

        @Tool("Search knowledge base: query, topK(number of results). Returns most relevant knowledge")
        public String kb_search(@P("query") String query,
                                @P("topK") Integer topK) {
            try {
                var results = ragService.search(query, topK == null ? 5 : topK, 0.3);
                if (results.isEmpty()) {
                    return "kb_search no results";
                }
                StringBuilder sb = new StringBuilder("kb_search results:\n");
                int idx = 1;
                for (var r : results) {
                    sb.append(idx++).append(". [score=").append(String.format("%.4f", r.getScore()))
                      .append("] ").append(r.getContent());
                    if (r.getSource() != null && !r.getSource().isEmpty()) {
                        sb.append(" (source: ").append(r.getSource()).append(")");
                    }
                    sb.append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return "kb_search error: " + e.getMessage();
            }
        }

        @Tool("Get knowledge base statistics: total document count")
        public String kb_stats() {
            try {
                long count = ragService.getDocumentCount();
                return "Knowledge base document count: " + count;
            } catch (Exception e) {
                return "Failed to get statistics: " + e.getMessage();
            }
        }

        @Tool("HTTP GET request returning response body (max 10000 chars). Only for trusted endpoints.")
        public String http_get(@P("url") String url) {
            if (url == null || url.trim().isEmpty()) {
                return "http_get failed: url cannot be empty";
            }
            String target = url.trim();

            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(target))
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> response =
                        client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

                String body = response.body();
                if (body != null && body.length() > 10000) {
                    body = body.substring(0, 10000) + "...";
                }

                return "http_get status=" + response.statusCode() + ", body=" + body;
            } catch (Exception e) {
                return "http_get error: " + e.getMessage();
            }
        }
    }

}
