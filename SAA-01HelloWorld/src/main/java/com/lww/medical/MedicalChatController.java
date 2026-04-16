package com.lww.medical;

import com.lww.mcp.McpToolService;
import com.lww.medical.context.HybridContextManager;
import com.lww.medical.dto.*;
import com.lww.medical.service.RedisMemoryService;
import com.lww.medical.session.*;
import com.lww.medical.tools.MedicalTools;
import com.lww.service.RagService;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医疗对话控制器
 * 集成 RAG 知识库和 MCP 工具，提供智能医疗问答服务
 */
@RestController
@RequestMapping("/api/medical")
public class MedicalChatController {

    private static final Logger log = LoggerFactory.getLogger(MedicalChatController.class);

    private final SessionManager sessionManager;
    private final HybridContextManager contextManager;
    private final SafetyGuard safetyGuard;
    private final ChatLanguageModel chatModel;
    private final MedicalTools medicalTools;
    private final RagService ragService;
    private final McpToolService mcpToolService;
    private final RedisMemoryService redisMemoryService;

    // 每个会话独立的助手实例（带独立记忆）
    private final Map<String, MedicalAssistant> assistantMap = new ConcurrentHashMap<>();

    public MedicalChatController(SessionManager sessionManager,
                                 HybridContextManager contextManager,
                                 SafetyGuard safetyGuard,
                                 ChatLanguageModel chatModel,
                                 MedicalTools medicalTools,
                                 RagService ragService,
                                 McpToolService mcpToolService,
                                 RedisMemoryService redisMemoryService) {
        this.sessionManager = sessionManager;
        this.contextManager = contextManager;
        this.safetyGuard = safetyGuard;
        this.chatModel = chatModel;
        this.medicalTools = medicalTools;
        this.ragService = ragService;
        this.mcpToolService = mcpToolService;
        this.redisMemoryService = redisMemoryService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatResponse response = new ChatResponse();
        long startTime = System.currentTimeMillis();

        // 1. 获取或创建会话
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            Session session = sessionManager.createSession(request.getPatientId());
            sessionId = session.getSessionId();
        }
        response.setSessionId(sessionId);

        // 2. 检测是否为紧急情况（但不拦截，只标记）
        boolean isEmergency = safetyGuard.detectEmergency(request.getMessage());
        response.setEmergency(isEmergency);

        // 3. 从 Milvus 知识库检索相关内容 (RAG增强)
        String ragContext = "";
        try {
            ragContext = ragService.buildContext(request.getMessage(), 3);
        } catch (Exception e) {
            log.debug("RAG retrieval failed: {}", e.getMessage());
            ragContext = "";
        }

        // 4. 检索用户历史健康记忆 (MCP Memory)
        String memoryContext = "";
        try {
            memoryContext = mcpToolService.retrieveMemory(sessionId, request.getMessage());
        } catch (Exception e) {
            log.debug("Memory retrieval failed: {}", e.getMessage());
        }

        // 5. 获取或创建该会话的助手（带独立记忆）
        MedicalAssistant assistant = assistantMap.computeIfAbsent(sessionId, id -> {
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
            return AiServices.builder(MedicalAssistant.class)
                    .chatLanguageModel(chatModel)
                    .tools(medicalTools)
                    .chatMemory(chatMemory)
                    .build();
        });

        // 6. 构建增强消息（RAG + Memory）
        String enhancedMessage = buildEnhancedMessage(request.getMessage(), ragContext, memoryContext);

        // 7. 调用 AI 生成回复
        String reply = assistant.chat(enhancedMessage);

        // 8. 内容过滤
        reply = safetyGuard.filterResponse(reply);

        // 9. 如果是紧急情况，在回答后追加紧急提醒
        if (isEmergency) {
            reply = reply + "\n\n" + safetyGuard.getEmergencyAlert();
        }

        // 10. 存储对话记忆到 MCP Memory
        try {
            mcpToolService.storeMemory(sessionId, request.getMessage(), "user");
            mcpToolService.storeMemory(sessionId, reply, "assistant");
        } catch (Exception e) {
            log.debug("Memory storage failed: {}", e.getMessage());
        }

        // 11. 存储对话记忆到 Redis（异步同步 MCP）
        String userId = request.getUserId() != null ? request.getUserId() : "default_user";
        try {
            // 保存用户消息
            String userMsgId = redisMemoryService.saveMessage(sessionId, userId, "user", request.getMessage());
            // 入队 MCP 同步
            redisMemoryService.enqueueMcpSync(sessionId, userMsgId, "user", request.getMessage());

            // 保存 AI 回复
            String assistantMsgId = redisMemoryService.saveMessage(sessionId, userId, "assistant", reply);
            // 入队 MCP 同步
            redisMemoryService.enqueueMcpSync(sessionId, assistantMsgId, "assistant", reply);
        } catch (Exception e) {
            log.debug("Redis memory storage failed: {}", e.getMessage());
        }

        response.setReply(reply);

        log.info("Chat processed in {}ms, sessionId: {}, emergency: {}",
                System.currentTimeMillis() - startTime, sessionId, isEmergency);

        return response;
    }

    /**
     * 构建增强消息
     */
    private String buildEnhancedMessage(String userMessage, String ragContext, String memoryContext) {
        StringBuilder sb = new StringBuilder();

        if (!memoryContext.isEmpty()) {
            sb.append("【用户历史健康信息】\n").append(memoryContext).append("\n\n");
        }

        if (!ragContext.isEmpty()) {
            sb.append("【知识库参考】\n").append(ragContext).append("\n\n");
        }

        sb.append("用户问题：").append(userMessage);

        return sb.toString();
    }

    /**
     * RAG 增强的医疗对话接口
     * 先从知识库检索相关内容，再结合上下文生成回复
     */
    @PostMapping("/chat/rag")
    public ChatResponse chatWithRag(@RequestBody ChatRequest request) {
        ChatResponse response = new ChatResponse();

        // 1. 获取或创建会话
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            Session session = sessionManager.createSession(request.getPatientId());
            sessionId = session.getSessionId();
        }
        response.setSessionId(sessionId);

        // 2. 检测是否为紧急情况（但不拦截，只标记）
        boolean isEmergency = safetyGuard.detectEmergency(request.getMessage());
        response.setEmergency(isEmergency);

        // 3. 从 Milvus 知识库检索相关内容
        String ragContext = "";
        try {
            ragContext = ragService.buildContext(request.getMessage(), 3);
        } catch (Exception e) {
            // RAG 检索失败不影响主流程
            ragContext = "";
        }

        // 4. 获取或创建该会话的助手
        MedicalAssistant assistant = assistantMap.computeIfAbsent(sessionId, id -> {
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
            return AiServices.builder(MedicalAssistant.class)
                    .chatLanguageModel(chatModel)
                    .tools(medicalTools)
                    .chatMemory(chatMemory)
                    .build();
        });

        // 5. 如果有 RAG 上下文，拼接到消息中
        String enhancedMessage = request.getMessage();
        if (!ragContext.isEmpty()) {
            enhancedMessage = ragContext + "\n用户问题：" + request.getMessage();
        }

        // 6. 调用 AI 生成回复
        String reply = assistant.chat(enhancedMessage);

        // 7. 内容过滤
        reply = safetyGuard.filterResponse(reply);

        // 8. 如果是紧急情况，在回答后追加紧急提醒
        if (isEmergency) {
            reply = reply + "\n\n" + safetyGuard.getEmergencyAlert();
        }

        response.setReply(reply);
        return response;
    }

    /**
     * 清除指定会话的上下文记忆
     */
    @DeleteMapping("/session/{sessionId}")
    public void clearSession(@PathVariable String sessionId) {
        assistantMap.remove(sessionId);
        sessionManager.removeSession(sessionId);
    }

    interface MedicalAssistant {
        @SystemMessage(
            "你是一位专业的中老年人智能医疗健康助手，名叫'嘎嘎'。\n" +
            "\n" +
            "你的职责：\n" +
            "1. 为中老年人提供健康咨询、疾病预防建议、用药指导\n" +
            "2. 用通俗易懂、亲切温和的语言与用户交流，避免专业术语\n" +
            "3. 关注用户描述的症状，提供合理的健康建议\n" +
            "4. 提醒用户定期体检、合理饮食、适量运动\n" +
            "5. 遇到紧急情况（胸痛、呼吸困难、中风症状等）立即建议就医\n" +
            "\n" +
            "回答原则：\n" +
            "- 语言简洁明了，适合老年人理解\n" +
            "- 语气亲切，像家人一样关心\n" +
            "- 在病情判断十分严重的时候需要认真的说语气不可十分轻松\n" +
            "- 不确定的问题要诚实说明，建议咨询医生\n" +
            "- 不做确诊，只提供健康建议\n" +
            "- 涉及用药问题，必须建议咨询医生或药师\n" +
            "- 记住用户之前提到的症状和病史，在后续对话中主动关心\n" +
            "- 如果参考知识库内容，要明确告知用户这是参考信息\n" +
            "\n" +
            "请始终以'康养小助手嘎嘎'的身份回答问题。"
        )
        String chat(String message);
    }
}
