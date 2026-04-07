package com.lww.controller;

import com.lww.mcp.McpToolService;
import com.lww.medical.SafetyGuard;
import com.lww.medical.tools.MedicalTools;
import com.lww.service.RagService;
import com.lww.user.entity.User;
import com.lww.user.repository.UserRepository;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 医疗健康流式聊天控制器
 * 集成 RAG + MCP Memory + 安全检测 + 用药提醒工具 + SSE 流式输出
 */
@RestController
@RequestMapping("/api/stream/medical")
public class MedicalStreamController {

    private static final Logger log = LoggerFactory.getLogger(MedicalStreamController.class);

    private final StreamingChatLanguageModel streamingModel;
    private final RagService ragService;
    private final McpToolService mcpToolService;
    private final SafetyGuard safetyGuard;
    private final MedicalTools medicalTools;
    private final UserRepository userRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 每个会话独立的助手实例（带独立记忆和工具）
    private final Map<String, MedicalStreamingAssistant> assistantMap = new ConcurrentHashMap<>();

    // 会话记忆存储（用于紧急检测等）
    private final Map<String, ChatMemory> sessionMemory = new ConcurrentHashMap<>();

    // 系统提示词
    private static final String MEDICAL_SYSTEM_PROMPT = """
            你是一位专业的中老年人智能医疗健康助手，名叫'嘎嘎'。

            你的职责：
            1. 为中老年人提供健康咨询、疾病预防建议、用药指导
            2. 用通俗易懂、亲切温和的语言与用户交流，避免专业术语
            3. 关注用户描述的症状，提供合理的健康建议
            4. 提醒用户定期体检、合理饮食、适量运动
            5. 遇到紧急情况（胸痛、呼吸困难、中风症状等）立即建议就医
            6. 当用户需要设置用药提醒时，调用 createMedicationReminderPlan 工具创建提醒
            7. 当用户需要查看用药提醒时，调用 getMyMedicationReminders 工具查询

            回答原则：
            - 语言简洁明了，适合老年人理解
            - 语气亲切，像家人一样关心
            - 在病情判断十分严重的时候需要认真的说语气不可十分轻松
            - 不确定的问题要诚实说明，建议咨询医生
            - 不做确诊，只提供健康建议
            - 涉及用药问题，必须建议咨询医生或药师
            - 记住用户之前提到的症状和病史，在后续对话中主动关心
            - 如果参考知识库内容，要明确告知用户这是参考信息
            - 用户说要设置用药提醒时，必须调用工具来创建，不要只是回复文字

            请始终以'康养小助手嘎嘎'的身份回答问题。
            """;

    public MedicalStreamController(
            StreamingChatLanguageModel streamingModel,
            RagService ragService,
            McpToolService mcpToolService,
            SafetyGuard safetyGuard,
            MedicalTools medicalTools,
            UserRepository userRepository) {
        this.streamingModel = streamingModel;
        this.ragService = ragService;
        this.mcpToolService = mcpToolService;
        this.safetyGuard = safetyGuard;
        this.medicalTools = medicalTools;
        this.userRepository = userRepository;
    }

    /**
     * 医疗流式聊天接口
     * 使用 AiServices 绑定工具，支持工具调用后继续流式输出
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter medicalStreamChat(@RequestBody MedicalStreamRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        // 在主线程获取认证信息，传递到异步线程
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Medical stream chat - authentication: {}, principal: {}",
                authentication != null ? authentication.getName() : "null",
                authentication != null ? authentication.getPrincipal() : "null");

        executor.execute(() -> {
            // 将认证信息设置到异步线程的 SecurityContext
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "medical_" + System.currentTimeMillis();
            }

            String finalSessionId = sessionId;

            // 绑定 sessionId 与 userId（用于工具调用时识别用户）
            if (authentication != null && !"anonymousUser".equals(authentication.getName())) {
                User user = userRepository.findByUsername(authentication.getName()).orElse(null);
                if (user != null) {
                    MedicalTools.bindSessionUser(finalSessionId, user.getId());
                    log.info("Bound sessionId {} to userId {}", finalSessionId, user.getId());
                }
            }

            try {
                // 1. 获取或创建助手（带工具和记忆）
                MedicalStreamingAssistant assistant = assistantMap.computeIfAbsent(finalSessionId, id -> {
                    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
                    sessionMemory.put(id, chatMemory);
                    return AiServices.builder(MedicalStreamingAssistant.class)
                            .streamingChatLanguageModel(streamingModel)
                            .tools(medicalTools)
                            .chatMemory(chatMemory)
                            .build();
                });

                // 2. 构建用户消息（包含 sessionId 供工具调用使用）
                String userMessage = request.getMessage();
                // 将 sessionId 注入到消息中，让 AI 在调用工具时可以传递
                String contextMessage = String.format("[会话ID: %s]\n\n%s", finalSessionId, userMessage);

                // 3. 紧急情况检测
                boolean isEmergency = safetyGuard.detectEmergency(userMessage);

                // 4. 如果是紧急情况，先发送紧急警告
                if (isEmergency) {
                    emitter.send(SseEmitter.event()
                            .name("emergency")
                            .data(safetyGuard.getEmergencyAlert()));
                }

                // 5. 流式生成回复（支持工具调用）
                StringBuilder fullResponse = new StringBuilder();
                final boolean[] connectionClosed = {false};

                TokenStream tokenStream = assistant.chat(contextMessage);

                tokenStream.onNext(token -> {
                    if (connectionClosed[0]) return;
                    // 检查 token 是否为空
                    if (token == null || token.isEmpty()) return;
                    try {
                        fullResponse.append(token);
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(token));
                    } catch (IOException e) {
                        connectionClosed[0] = true;
                        log.debug("Client disconnected during streaming: {}", e.getMessage());
                    }
                });

                tokenStream.onComplete(response -> {
                    if (connectionClosed[0]) {
                        log.debug("Connection already closed, skip completion");
                        return;
                    }
                    try {
                        // 发送 done 事件
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(""));
                        emitter.complete();

                        // 内容安全过滤
                        String filteredResponse = safetyGuard.filterResponse(fullResponse.toString());

                        // 如果是紧急情况，追加紧急提醒
                        if (isEmergency) {
                            String emergencyAlert = safetyGuard.getEmergencyAlert();
                            emitter.send(SseEmitter.event()
                                    .name("emergency_append")
                                    .data("\n\n" + emergencyAlert));
                            filteredResponse += "\n\n" + emergencyAlert;
                        }

                        // 存储到 MCP Memory
                        try {
                            mcpToolService.storeMemory(finalSessionId, request.getMessage(), "user");
                            mcpToolService.storeMemory(finalSessionId, filteredResponse, "assistant");
                        } catch (Exception e) {
                            log.debug("Memory storage failed: {}", e.getMessage());
                        }

                        log.info("Medical stream completed, sessionId: {}, emergency: {}, length: {}",
                                finalSessionId, isEmergency, filteredResponse.length());
                    } catch (IOException e) {
                        log.debug("SSE complete error: {}", e.getMessage());
                    }
                });

                tokenStream.onError(error -> {
                    connectionClosed[0] = true;
                    log.error("Medical stream error: {}", error.getMessage());
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("服务暂时不可用，请稍后重试"));
                        emitter.completeWithError(error);
                    } catch (IOException e) {
                        log.debug("SSE error send failed: {}", e.getMessage());
                    }
                });

                // 启动流式处理
                tokenStream.start();

            } catch (Exception e) {
                log.error("Medical stream chat error: {}", e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        emitter.onTimeout(() -> {
            log.warn("Medical SSE connection timeout");
            emitter.complete();
        });

        emitter.onCompletion(() -> log.debug("Medical SSE connection closed"));

        return emitter;
    }

    /**
     * 流式医疗助手接口（支持工具调用）
     */
    interface MedicalStreamingAssistant {
        @dev.langchain4j.service.SystemMessage("""
            你是一位专业的中老年人智能医疗健康助手，名叫'嘎嘎'。

            你的职责：
            1. 为中老年人提供健康咨询、疾病预防建议、用药指导
            2. 用通俗易懂、亲切温和的语言与用户交流，避免专业术语
            3. 关注用户描述的症状，提供合理的健康建议
            4. 提醒用户定期体检、合理饮食、适量运动
            5. 遇到紧急情况（胸痛、呼吸困难、中风症状等）立即建议就医
            6. 当用户需要设置用药提醒时，必须调用 createMedicationReminderPlan 工具创建提醒
            7. 当用户需要查看用药提醒时，必须调用 getMyMedicationReminders 工具查询

            重要：用户消息开头可能包含 [会话ID: xxx] 格式，调用工具时必须将这个会话ID作为第一个参数传递给工具。

            回答原则：
            - 语言简洁明了，适合老年人理解
            - 语气亲切，像家人一样关心
            - 在病情判断十分严重的时候需要认真的说语气不可十分轻松
            - 不确定的问题要诚实说明，建议咨询医生
            - 不做确诊，只提供健康建议
            - 涉及用药问题，必须建议咨询医生或药师
            - 记住用户之前提到的症状和病史，在后续对话中主动关心
            - 如果参考知识库内容，要明确告知用户这是参考信息
            - 用户说要设置用药提醒时，必须调用工具来创建，不要只是回复文字
            - 用户说要查看用药提醒时，必须调用工具来查询

            请始终以'康养小助手嘎嘎'的身份回答问题。
            """)
        TokenStream chat(String message);
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/session/{sessionId}")
    public void clearSession(@PathVariable String sessionId) {
        sessionMemory.remove(sessionId);
        log.info("Medical session cleared: {}", sessionId);
    }

    /**
     * 请求 DTO
     */
    public static class MedicalStreamRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
