package com.lww.medical;

import com.lww.medical.context.HybridContextManager;
import com.lww.medical.dto.*;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医疗对话控制器
 * 集成 RAG 知识库，提供智能医疗问答服务
 */
@RestController
@RequestMapping("/api/medical")
public class MedicalChatController {

    private final SessionManager sessionManager;
    private final HybridContextManager contextManager;
    private final SafetyGuard safetyGuard;
    private final ChatLanguageModel chatModel;
    private final MedicalTools medicalTools;
    private final RagService ragService;

    // 每个会话独立的助手实例（带独立记忆）
    private final Map<String, MedicalAssistant> assistantMap = new ConcurrentHashMap<>();

    public MedicalChatController(SessionManager sessionManager,
                                 HybridContextManager contextManager,
                                 SafetyGuard safetyGuard,
                                 ChatLanguageModel chatModel,
                                 MedicalTools medicalTools,
                                 RagService ragService) {
        this.sessionManager = sessionManager;
        this.contextManager = contextManager;
        this.safetyGuard = safetyGuard;
        this.chatModel = chatModel;
        this.medicalTools = medicalTools;
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatResponse response = new ChatResponse();

        // 1. 获取或创建会话
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            Session session = sessionManager.createSession(request.getPatientId());
            sessionId = session.getSessionId();
        }
        response.setSessionId(sessionId);

        // 2. 安全检查
        if (safetyGuard.detectEmergency(request.getMessage())) {
            response.setEmergency(true);
            response.setReply(safetyGuard.getEmergencyAlert());
            return response;
        }

        // 3. 从 Milvus 知识库检索相关内容 (RAG增强)
        String ragContext = "";
        try {
            ragContext = ragService.buildContext(request.getMessage(), 3);
        } catch (Exception e) {
            // RAG 检索失败不影响主流程
            ragContext = "";
        }

        // 4. 获取或创建该会话的助手（带独立记忆）
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

        response.setReply(reply);
        return response;
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

        // 2. 安全检查
        if (safetyGuard.detectEmergency(request.getMessage())) {
            response.setEmergency(true);
            response.setReply(safetyGuard.getEmergencyAlert());
            return response;
        }

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
