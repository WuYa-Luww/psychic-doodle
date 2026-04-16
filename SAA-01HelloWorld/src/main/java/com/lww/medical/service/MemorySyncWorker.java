package com.lww.medical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lww.mcp.McpToolService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 同步 Worker
 * 异步消费 Redis 队列中的待同步消息，调用 MCP storeMemory
 */
@Component
public class MemorySyncWorker {

    private static final Logger log = LoggerFactory.getLogger(MemorySyncWorker.class);
    private static final int MAX_RETRY_COUNT = 3;

    @Autowired
    private RedisMemoryService redisMemoryService;

    @Autowired(required = false)
    private McpToolService mcpToolService;

    private boolean mcpAvailable = false;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        mcpAvailable = (mcpToolService != null);
        if (mcpAvailable) {
            log.info("MCP Memory Sync Worker 已启动，MCP 服务可用");
        } else {
            log.warn("MCP Memory Sync Worker 已启动，但 MCP 服务不可用，同步功能已禁用");
        }
    }

    /**
     * 定时任务：每 5 秒检查 MCP 重试队列
     */
    @Scheduled(fixedDelay = 5000)
    @SuppressWarnings("unchecked")
    public void processRetryQueue() {
        if (!mcpAvailable) {
            return;
        }

        try {
            String json = redisMemoryService.pollMcpSyncQueue();
            if (json == null) {
                return; // 队列为空
            }

            // 解析 JSON
            Map<String, Object> item = objectMapper.readValue(json, Map.class);
            String messageId = (String) item.get("messageId");
            String sessionId = (String) item.get("sessionId");
            String role = (String) item.get("role");
            String content = (String) item.get("content");
            int retryCount = item.get("retryCount") != null ? ((Number) item.get("retryCount")).intValue() : 0;

            // 调用 MCP storeMemory
            try {
                String result = mcpToolService.storeMemory(sessionId, content, role);

                // storeMemory 返回空字符串表示成功（其他情况返回错误信息）
                if (result != null && !result.contains("失败") && !result.contains("异常") && !result.contains("错误")) {
                    // 同步成功，记录状态
                    redisMemoryService.recordMcpSyncStatus(messageId, retryCount, null);
                    log.info("MCP 同步成功: messageId={}, sessionId={}", messageId, sessionId);
                } else {
                    // 同步失败，尝试重试
                    handleSyncFailure(messageId, sessionId, role, content, retryCount, result);
                }
            } catch (Exception e) {
                // 同步异常，尝试重试
                handleSyncFailure(messageId, sessionId, role, content, retryCount, e.getMessage());
            }
        } catch (Exception e) {
            log.error("处理 MCP 同步队列异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理同步失败（重试逻辑）
     */
    private void handleSyncFailure(String messageId, String sessionId, String role, String content, int currentRetryCount, String errorMsg) {
        int newRetryCount = currentRetryCount + 1;

        if (newRetryCount > MAX_RETRY_COUNT) {
            // 超过最大重试次数，记录死信日志并告警
            log.error("MCP 同步死信: messageId={}, sessionId={}, error={}, retryCount={}",
                    messageId, sessionId, errorMsg, newRetryCount);
            // 记录同步状态（失败）
            redisMemoryService.recordMcpSyncStatus(messageId, newRetryCount, errorMsg);

            // TODO: 可以发送告警通知（如邮件、钉钉等）
            log.warn("MCP 同步超过最大重试次数，请检查 MCP 服务状态！");
        } else {
            // 重新入队（指数退避：delay = retryCount * 5 秒）
            long delayMs = newRetryCount * 5 * 1000L;

            // 简单实现：立即重新入队（实际生产环境可用延时队列）
            try {
                Map<String, Object> retryItem = new HashMap<>();
                retryItem.put("messageId", messageId);
                retryItem.put("sessionId", sessionId);
                retryItem.put("role", role);
                retryItem.put("content", content);
                retryItem.put("retryCount", newRetryCount);
                retryItem.put("timestamp", System.currentTimeMillis());

                String json = objectMapper.writeValueAsString(retryItem);
                // 重新入队
                redisMemoryService.requeueWithDelay(json);
                redisMemoryService.recordMcpSyncStatus(messageId, newRetryCount, errorMsg);

                log.warn("MCP 同步失败，准备重试: messageId={}, retryCount={}, delay={}ms, error={}",
                        messageId, newRetryCount, delayMs, errorMsg);
            } catch (Exception e) {
                log.error("重新入队失败: messageId={}, error={}", messageId, e.getMessage());
            }
        }
    }
}
