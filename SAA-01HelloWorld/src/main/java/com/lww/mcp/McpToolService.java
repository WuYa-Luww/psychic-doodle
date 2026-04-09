package com.lww.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具服务
 * 封装对 Memory、Sequential Thinking、Time MCP 服务器的调用
 */
@Service
public class McpToolService {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

    private final McpSyncClient memoryMcpClient;
    private final McpSyncClient sequentialThinkingMcpClient;
    private final McpSyncClient timeMcpClient;

    public McpToolService(@Qualifier("memoryMcpClient") McpSyncClient memoryMcpClient,
                          @Qualifier("sequentialThinkingMcpClient") McpSyncClient sequentialThinkingMcpClient,
                          @Qualifier("timeMcpClient") McpSyncClient timeMcpClient) {
        this.memoryMcpClient = memoryMcpClient;
        this.sequentialThinkingMcpClient = sequentialThinkingMcpClient;
        this.timeMcpClient = timeMcpClient;
    }

    // ==================== Memory MCP 工具 ====================

    /**
     * 存储用户对话记忆到知识图谱
     *
     * @param sessionId 会话ID
     * @param message   消息内容
     * @param role      角色（user/assistant）
     * @return 操作结果
     */
    public String storeMemory(String sessionId, String message, String role) {
        try {
            McpSchema.CallToolResult result = memoryMcpClient.callTool(
                    new McpSchema.CallToolRequest(
                            "store_memory",
                            Map.of(
                                    "session_id", sessionId,
                                    "message", message,
                                    "role", role
                            )
                    )
            );
            return extractTextResult(result);
        } catch (Exception e) {
            log.warn("Memory MCP store failed: {}", e.getMessage());
            return "记忆存储失败";
        }
    }

    /**
     * 检索用户历史记忆
     *
     * @param sessionId 会话ID
     * @param query     查询内容
     * @return 相关记忆内容
     */
    public String retrieveMemory(String sessionId, String query) {
        try {
            McpSchema.CallToolResult result = memoryMcpClient.callTool(
                    new McpSchema.CallToolRequest(
                            "retrieve_memory",
                            Map.of(
                                    "session_id", sessionId,
                                    "query", query
                            )
                    )
            );
            return extractTextResult(result);
        } catch (Exception e) {
            log.warn("Memory MCP retrieve failed: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取用户完整对话历史
     *
     * @param sessionId 会话ID
     * @return 对话历史
     */
    public String getConversationHistory(String sessionId) {
        try {
            McpSchema.CallToolResult result = memoryMcpClient.callTool(
                    new McpSchema.CallToolRequest(
                            "get_conversation_history",
                            Map.of("session_id", sessionId)
                    )
            );
            return extractTextResult(result);
        } catch (Exception e) {
            log.warn("Memory MCP get history failed: {}", e.getMessage());
            return "";
        }
    }

    // ==================== Sequential Thinking MCP 工具 ====================

    /**
     * 结构化思考推理
     * 用于复杂症状分析和医疗决策推理
     *
     * @param problem   待分析的问题
     * @param context   相关上下文信息
     * @return 推理过程和结论
     */
    public String sequentialThinking(String problem, String context) {
        try {
            McpSchema.CallToolResult result = sequentialThinkingMcpClient.callTool(
                    new McpSchema.CallToolRequest(
                            "sequential_thinking",
                            Map.of(
                                    "problem", problem,
                                    "context", context,
                                    "thought_number", 1,
                                    "total_thoughts", 5,
                                    "is_medical_analysis", true
                            )
                    )
            );
            return extractTextResult(result);
        } catch (Exception e) {
            log.warn("Sequential Thinking MCP failed: {}", e.getMessage());
            return "推理分析暂时不可用";
        }
    }

    /**
     * 多步骤症状分析推理
     *
     * @param symptoms 症状描述
     * @return 分析推理过程
     */
    public String analyzeSymptomsWithReasoning(String symptoms) {
        String problem = String.format(
                "分析以下症状，评估可能的健康问题，并提供就诊建议：%s",
                symptoms
        );
        return sequentialThinking(problem, "");
    }

    // ==================== Time MCP 工具 ====================

    /**
     * 获取当前时间
     *
     * @param timezone 时区（可选，如 "Asia/Shanghai"）
     * @return 当前时间
     */
    public String getCurrentTime(String timezone) {
        try {
            Map<String, Object> args = timezone != null && !timezone.isEmpty()
                    ? Map.of("timezone", timezone)
                    : Map.of();

            McpSchema.CallToolResult result = timeMcpClient.callTool(
                    new McpSchema.CallToolRequest("get_current_time", args)
            );
            return extractTextResult(result);
        } catch (Exception e) {
            log.warn("Time MCP get current time failed: {}", e.getMessage());
            return java.time.LocalDateTime.now().toString();
        }
    }

    /**
     * 时区转换
     *
     * @param sourceTime     源时间字符串
     * @param sourceZone     源时区
     * @param targetZone     目标时区
     * @return 转换后的时间
     */
    public String convertTimezone(String sourceTime, String sourceZone, String targetZone) {
        try {
            McpSchema.CallToolResult result = timeMcpClient.callTool(
                    new McpSchema.CallToolRequest(
                            "convert_timezone",
                            Map.of(
                                    "source_time", sourceTime,
                                    "source_timezone", sourceZone,
                                    "target_timezone", targetZone
                            )
                    )
            );
            return extractTextResult(result);
        } catch (Exception e) {
            log.warn("Time MCP convert timezone failed: {}", e.getMessage());
            return sourceTime;
        }
    }

    /**
     * 创建用药提醒
     *
     * @param medicationName 药品名称
     * @param time           服用时间
     * @param instructions   用药说明
     * @return 提醒信息
     */
    public String createMedicationReminder(String medicationName, String time, String instructions) {
        return String.format(
                "【用药提醒】%s\n服用时间：%s\n用药说明：%s",
                medicationName,
                time,
                instructions
        );
    }

    /**
     * 计算下次服药时间
     *
     * @param lastTakenTime 上次服药时间
     * @param intervalHours 间隔小时数
     * @return 下次服药时间
     */
    public String calculateNextDoseTime(String lastTakenTime, int intervalHours) {
        try {
            // 使用当前时间作为基础计算
            String currentTime = getCurrentTime("Asia/Shanghai");
            return String.format(
                    "上次服药时间：%s\n建议下次服药时间：%d小时后\n当前时间：%s",
                    lastTakenTime,
                    intervalHours,
                    currentTime
            );
        } catch (Exception e) {
            return "时间计算暂时不可用";
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 MCP 调用结果中提取文本内容
     */
    private String extractTextResult(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                sb.append(textContent.text());
            }
        }
        return sb.toString();
    }
}
