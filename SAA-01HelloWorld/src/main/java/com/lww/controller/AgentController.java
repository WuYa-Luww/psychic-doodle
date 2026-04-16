package com.lww.controller;

import com.lww.medical.service.RedisMemoryService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AgentController {

    private final AgentAssistant agentAssistant;
    private final RedisMemoryService redisMemoryService;

    public AgentController(AgentAssistant agentAssistant, RedisMemoryService redisMemoryService) {
        this.agentAssistant = agentAssistant;
        this.redisMemoryService = redisMemoryService;
    }

    @PostMapping("/agent")
    public ChatResponse agent(@RequestBody ChatRequest request) {
        ChatResponse response = new ChatResponse();

        // 获取或创建会话 ID
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        response.setSessionId(sessionId);

        // 获取用户 ID（默认 default_user）
        String userId = request.getUserId() != null ? request.getUserId() : "default_user";

        // 调用 AI 助手
        String reply = agentAssistant.chat(request.getInput());
        response.setReply(reply);

        // 存储对话到 Redis（异步同步 MCP）
        try {
            // 保存用户消息
            String userMsgId = redisMemoryService.saveMessage(sessionId, userId, "user", request.getInput());
            redisMemoryService.enqueueMcpSync(sessionId, userMsgId, "user", request.getInput());

            // 保存 AI 回复
            String assistantMsgId = redisMemoryService.saveMessage(sessionId, userId, "assistant", reply);
            redisMemoryService.enqueueMcpSync(sessionId, assistantMsgId, "assistant", reply);
        } catch (Exception e) {
            // Redis 存储失败不影响主流程
        }

        return response;
    }

    /**
     * 响应对象
     */
    public static class ChatResponse {
        private String sessionId;
        private String reply;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }
    }

    /**
     * AiServices 会把该接口方法当作 Agent 的入口。
     * 通过 SystemMessage/用户消息模板引导"何时使用工具"。
     */
    public interface AgentAssistant {

        @SystemMessage("你是一个中文智能助手。\n"
                + "你拥有工具能力：当用户需要【实时确定信息】（例如当前时间/时间戳换算）时，必须先调用工具获得结果。\n"
                + "当用户需要【知识库内容】（例如：让你查询某段知识/回答需要依据你写入的知识库）时，必须先调用工具 kb_search 获取相关片段，并只基于片段回答。\n"
                + "当用户要求你新增/更新知识库时，必须调用 kb_put 写入。\n"
                + "当用户要求按 kbId 读取知识库时，必须调用 kb_get。\n"
                + "回答要简洁、准确，并优先引用工具返回的内容。")
        @UserMessage("{{it}}")
        String chat(String userMessage);
    }
}
