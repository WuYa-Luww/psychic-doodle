package com.lww.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 流式聊天控制器
 * 实现 Server-Sent Events 流式输出
 */
@RestController
@RequestMapping("/api/stream")
public class StreamChatController {

    private static final Logger log = LoggerFactory.getLogger(StreamChatController.class);

    private final StreamingChatLanguageModel streamingModel;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public StreamChatController(StreamingChatLanguageModel streamingModel) {
        this.streamingModel = streamingModel;
    }

    /**
     * SSE 流式聊天接口
     *
     * 使用方式：
     * 前端通过 EventSource 或 fetch + ReadableStream 接收流式数据
     *
     * 示例（JavaScript）：
     * const eventSource = new EventSource('/api/stream/chat?message=你好');
     * eventSource.onmessage = (event) => {
     *     console.log(event.data); // 每次输出的文字片段
     * };
     * eventSource.onerror = () => eventSource.close();
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message) {
        SseEmitter emitter = new SseEmitter(60_000L); // 60秒超时

        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final boolean[] connectionClosed = {false};

            emitter.onCompletion(() -> connectionClosed[0] = true);
            emitter.onTimeout(() -> {
                connectionClosed[0] = true;
                log.warn("SSE connection timeout");
            });

            try {
                streamingModel.generate(message, new StreamingResponseHandler<AiMessage>() {
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
                            log.debug("Client disconnected: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        if (connectionClosed[0]) return;
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(""));
                            emitter.complete();
                            log.debug("Stream completed, total length: {}", fullResponse.length());
                        } catch (IOException e) {
                            log.debug("SSE complete error: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        connectionClosed[0] = true;
                        log.error("Stream error: {}", error.getMessage());
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("服务暂时不可用"));
                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            log.debug("SSE error send failed: {}", e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                log.error("Stream chat error: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * POST 方式流式聊天（支持更长消息）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatPost(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);

        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final boolean[] connectionClosed = {false};

            emitter.onCompletion(() -> connectionClosed[0] = true);
            emitter.onTimeout(() -> {
                connectionClosed[0] = true;
                log.warn("SSE connection timeout");
            });

            try {
                streamingModel.generate(request.getInput(), new StreamingResponseHandler<AiMessage>() {
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
                            log.debug("Client disconnected: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        if (connectionClosed[0]) return;
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(""));
                            emitter.complete();
                            log.debug("Stream completed, total length: {}", fullResponse.length());
                        } catch (IOException e) {
                            log.debug("SSE complete error: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        connectionClosed[0] = true;
                        log.error("Stream error: {}", error.getMessage());
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("服务暂时不可用"));
                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            log.debug("SSE error send failed: {}", e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                log.error("Stream chat error: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
