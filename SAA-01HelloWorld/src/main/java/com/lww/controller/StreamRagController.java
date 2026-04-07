package com.lww.controller;

import com.lww.service.RagService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RAG + SSE 流式聊天控制器
 * 先从知识库检索相关内容，再流式输出回答
 */
@RestController
@RequestMapping("/api/stream/rag")
public class StreamRagController {

    private static final Logger log = LoggerFactory.getLogger(StreamRagController.class);

    private final StreamingChatLanguageModel streamingModel;
    private final RagService ragService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 会话记忆存储（生产环境应使用 Redis）
    private final Map<String, List<ChatMessage>> sessionMemory = new ConcurrentHashMap<>();

    public StreamRagController(StreamingChatLanguageModel streamingModel, RagService ragService) {
        this.streamingModel = streamingModel;
        this.ragService = ragService;
    }

    /**
     * RAG 增强的流式聊天
     *
     * SSE 事件格式：
     * event: message
     * data: 文字片段
     *
     * event: rag_context
     * data: 检索到的知识库内容
     *
     * event: done
     * data: [DONE]
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ragStreamChat(@RequestBody StreamChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时

        executor.execute(() -> {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "session_" + System.currentTimeMillis();
            }

            String finalSessionId = sessionId;

            // 获取或创建会话记忆
            List<ChatMessage> memory = sessionMemory.computeIfAbsent(finalSessionId, k -> new ArrayList<>());

            // 添加系统提示
            if (memory.isEmpty()) {
                memory.add(new SystemMessage(
                        "你是一个智能助手，请基于提供的知识库内容回答用户问题。\n" +
                        "如果知识库内容与问题相关，请结合知识库内容作答。\n" +
                        "如果知识库内容不相关，请根据你的知识回答。\n" +
                        "回答要简洁、准确、有帮助。"
                ));
            }

            StringBuilder fullResponse = new StringBuilder();

            try {
                // 1. RAG 检索
                String ragContext = "";
                try {
                    ragContext = ragService.buildContext(request.getMessage(), 3);
                    if (!ragContext.isEmpty()) {
                        // 发送 RAG 上下文事件
                        emitter.send(SseEmitter.event()
                                .name("rag_context")
                                .data(ragContext));
                    }
                } catch (Exception e) {
                    log.debug("RAG retrieval failed: {}", e.getMessage());
                }

                // 2. 构建增强消息
                String enhancedMessage = request.getMessage();
                if (!ragContext.isEmpty()) {
                    enhancedMessage = ragContext + "\n\n用户问题：" + request.getMessage();
                }

                // 3. 添加用户消息到记忆
                memory.add(new UserMessage(enhancedMessage));

                // 4. 流式生成回复
                final boolean[] connectionClosed = {false};

                streamingModel.generate(memory, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (connectionClosed[0]) return;
                        try {
                            fullResponse.append(token);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(token));
                        } catch (IOException e) {
                            connectionClosed[0] = true;
                            log.debug("Client disconnected during streaming: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        if (connectionClosed[0]) return;
                        try {
                            // 添加 AI 回复到记忆
                            memory.add(new AiMessage(fullResponse.toString()));

                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(""));
                            emitter.complete();
                            log.info("RAG Stream completed, sessionId: {}, response length: {}",
                                    finalSessionId, fullResponse.length());
                        } catch (IOException e) {
                            log.debug("SSE complete error (client likely disconnected): {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        connectionClosed[0] = true;
                        log.error("RAG Stream error: {}", error.getMessage());
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("服务暂时不可用，请稍后重试"));
                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            log.debug("SSE error send failed: {}", e.getMessage());
                        }
                    }
                });

            } catch (Exception e) {
                log.error("RAG Stream chat error: {}", e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout");
            emitter.complete();
        });

        emitter.onCompletion(() -> log.debug("SSE connection closed"));

        return emitter;
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/session/{sessionId}")
    public void clearSession(@PathVariable String sessionId) {
        sessionMemory.remove(sessionId);
        log.info("Session cleared: {}", sessionId);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public Map<String, Object> getSessions() {
        return Map.of(
                "activeSessions", sessionMemory.size(),
                "sessionIds", sessionMemory.keySet()
        );
    }

    /**
     * 请求 DTO
     */
    public static class StreamChatRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
