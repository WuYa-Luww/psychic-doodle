package com.lww.medical.service;

import com.lww.medical.dto.MessageDTO;
import com.lww.medical.dto.SessionDetailDTO;
import com.lww.medical.dto.SessionSummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分层记忆管理服务
 *
 * 数据模型:
 * 1. chat:session:{sessionId} → Hash (会话元数据)
 * 2. chat:session:{sessionId}:messages → List (消息ID列表)
 * 3. chat:message:{messageId} → Hash (消息内容)
 * 4. chat:user:{userId}:sessions → ZSET (用户会话索引，score=lastActiveAt)
 * 5. chat:mcp:retry:queue → List (MCP 同步重试队列)
 * 6. chat:mcp:sync:status:{messageId} → Hash (同步状态)
 */
@Service
public class RedisMemoryService {

    // Redis Key 前缀常量
    private static final String KEY_SESSION_PREFIX = "chat:session:";
    private static final String KEY_MESSAGE_PREFIX = "chat:message:";
    private static final String KEY_USER_SESSIONS_PREFIX = "chat:user:";
    private static final String KEY_MCP_RETRY_QUEUE = "chat:mcp:retry:queue";
    private static final String KEY_MCP_SYNC_STATUS_PREFIX = "chat:mcp:sync:status:";

    // TTL 配置（毫秒）
    private static final long SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000;   // 7 天
    private static final long MESSAGE_TTL_MS = 30L * 24 * 60 * 60 * 1000;  // 30 天

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 保存消息到 Redis
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param role      角色 (user/assistant)
     * @param content   消息内容
     * @return 生成的消息 ID
     */
    public String saveMessage(String sessionId, String userId, String role, String content) {
        long now = Instant.now().toEpochMilli();

        // 1. 生成消息 sequence（原子递增）
        String sessionSeqKey = KEY_SESSION_PREFIX + sessionId + ":seq";
        Long sequence = redisTemplate.opsForValue().increment(sessionSeqKey);
        if (sequence == null) {
            sequence = 1L;
        }

        // 2. 生成消息 ID
        String messageId = sessionId + ":" + sequence;

        // 3. 首条消息生成会话标题（前8个字）
        if (sequence == 1) {
            String title = content.length() <= 8 ? content : content.substring(0, 8) + "...";
            Map<String, Object> sessionMeta = new HashMap<>();
            sessionMeta.put("title", title);
            sessionMeta.put("createdAt", now);
            sessionMeta.put("lastActiveAt", now);
            sessionMeta.put("status", "ACTIVE");
            sessionMeta.put("messageCount", 1L);
            sessionMeta.put("userId", userId);
            redisTemplate.opsForHash().putAll(KEY_SESSION_PREFIX + sessionId, sessionMeta);
            redisTemplate.expire(KEY_SESSION_PREFIX + sessionId, SESSION_TTL_MS, TimeUnit.MILLISECONDS);
        } else {
            // 更新会话元数据
            redisTemplate.opsForHash().put(KEY_SESSION_PREFIX + sessionId, "lastActiveAt", now);
            redisTemplate.opsForHash().increment(KEY_SESSION_PREFIX + sessionId, "messageCount", 1);
            redisTemplate.expire(KEY_SESSION_PREFIX + sessionId, SESSION_TTL_MS, TimeUnit.MILLISECONDS);
        }

        // 4. 存储消息内容 Hash
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("sessionId", sessionId);
        messageData.put("role", role);
        messageData.put("content", content);
        messageData.put("timestamp", now);
        messageData.put("sequence", sequence);
        redisTemplate.opsForHash().putAll(KEY_MESSAGE_PREFIX + messageId, messageData);
        redisTemplate.expire(KEY_MESSAGE_PREFIX + messageId, MESSAGE_TTL_MS, TimeUnit.MILLISECONDS);

        // 5. 消息 ID 加入会话消息列表（正序 LPUSH）
        redisTemplate.opsForList().leftPush(KEY_SESSION_PREFIX + sessionId + ":messages", messageId);
        redisTemplate.expire(KEY_SESSION_PREFIX + sessionId + ":messages", SESSION_TTL_MS, TimeUnit.MILLISECONDS);

        // 6. 更新用户会话索引 ZSET（按最后活跃时间排序）
        String userSessionsKey = KEY_USER_SESSIONS_PREFIX + userId + ":sessions";
        redisTemplate.opsForZSet().add(userSessionsKey, sessionId, (double) now);
        redisTemplate.expire(userSessionsKey, SESSION_TTL_MS, TimeUnit.MILLISECONDS);

        return messageId;
    }

    /**
     * 获取用户的会话列表（按最后活跃时间倒序，最多 20 条）
     *
     * @param userId 用户 ID
     * @return 会话摘要列表
     */
    public List<SessionSummaryDTO> getSessions(String userId) {
        String userSessionsKey = KEY_USER_SESSIONS_PREFIX + userId + ":sessions";

        // 按 score 倒序获取（最近活跃优先）
        Set<Object> rawSessionIds = redisTemplate.opsForZSet().reverseRange(userSessionsKey, 0, 19);

        List<SessionSummaryDTO> result = new ArrayList<>();
        if (rawSessionIds == null || rawSessionIds.isEmpty()) {
            return result;
        }

        for (Object rawId : rawSessionIds) {
            String sessionId = rawId.toString();
            // 检查会话状态（过滤已删除）
            Object statusObj = redisTemplate.opsForHash().get(KEY_SESSION_PREFIX + sessionId, "status");
            if (statusObj != null && "DELETED".equals(statusObj.toString())) {
                continue;
            }

            Map<Object, Object> rawMeta = redisTemplate.opsForHash().entries(KEY_SESSION_PREFIX + sessionId);
            if (rawMeta != null && !rawMeta.isEmpty()) {
                SessionSummaryDTO dto = new SessionSummaryDTO();
                dto.setSessionId(sessionId);
                dto.setTitle(rawMeta.get("title") != null ? rawMeta.get("title").toString() : null);
                dto.setMessageCount(rawMeta.get("messageCount") != null ? ((Number) rawMeta.get("messageCount")).longValue() : 0L);
                dto.setLastActiveAt(rawMeta.get("lastActiveAt") != null ? ((Number) rawMeta.get("lastActiveAt")).longValue() : 0L);
                dto.setCreatedAt(rawMeta.get("createdAt") != null ? ((Number) rawMeta.get("createdAt")).longValue() : 0L);
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * 获取会话详情（分页消息）
     *
     * @param sessionId 会话 ID
     * @param limit     每页数量
     * @param offset    偏移量
     * @return 会话详情 DTO
     */
    public SessionDetailDTO getSessionDetail(String sessionId, int limit, int offset) {
        String sessionKey = KEY_SESSION_PREFIX + sessionId;
        String messagesKey = sessionKey + ":messages";

        // 获取会话元数据
        Map<Object, Object> rawMeta = redisTemplate.opsForHash().entries(sessionKey);
        if (rawMeta == null || rawMeta.isEmpty()) {
            return null;
        }

        // 获取消息 ID 列表（LPUSH 是头插，所以 LRANGE 返回的是倒序，需要反转）
        List<Object> rawMessageIds = redisTemplate.opsForList().range(messagesKey, offset, offset + limit - 1);
        if (rawMessageIds != null) {
            java.util.Collections.reverse(rawMessageIds);
        }

        List<MessageDTO> messages = new ArrayList<>();
        if (rawMessageIds != null && !rawMessageIds.isEmpty()) {
            // 批量获取消息内容
            for (Object rawMid : rawMessageIds) {
                String messageId = rawMid.toString();
                Map<Object, Object> data = redisTemplate.opsForHash().entries(KEY_MESSAGE_PREFIX + messageId);
                if (data != null && !data.isEmpty()) {
                    MessageDTO msg = new MessageDTO();
                    msg.setMessageId(messageId);
                    msg.setRole(data.get("role") != null ? data.get("role").toString() : null);
                    msg.setContent(data.get("content") != null ? data.get("content").toString() : null);
                    msg.setTimestamp(data.get("timestamp") != null ? ((Number) data.get("timestamp")).longValue() : 0L);
                    msg.setSequence(data.get("sequence") != null ? ((Number) data.get("sequence")).longValue() : 0L);
                    messages.add(msg);
                }
            }
        }

        SessionDetailDTO detail = new SessionDetailDTO();
        detail.setSessionId(sessionId);
        detail.setTitle(rawMeta.get("title") != null ? rawMeta.get("title").toString() : null);
        detail.setCreatedAt(rawMeta.get("createdAt") != null ? ((Number) rawMeta.get("createdAt")).longValue() : 0L);
        detail.setLastActiveAt(rawMeta.get("lastActiveAt") != null ? ((Number) rawMeta.get("lastActiveAt")).longValue() : 0L);
        detail.setMessageCount(rawMeta.get("messageCount") != null ? ((Number) rawMeta.get("messageCount")).longValue() : 0L);
        detail.setMessages(messages);

        return detail;
    }

    /**
     * 软删除会话（标记为 DELETED，从用户索引移除，数据保留）
     *
     * @param sessionId 会话 ID
     */
    public void softDeleteSession(String sessionId) {
        String sessionKey = KEY_SESSION_PREFIX + sessionId;

        // 获取用户 ID
        Object userIdObj = redisTemplate.opsForHash().get(sessionKey, "userId");
        if (userIdObj == null) {
            return;
        }
        String userId = userIdObj.toString();

        // 标记会话状态为 DELETED
        redisTemplate.opsForHash().put(sessionKey, "status", "DELETED");

        // 从用户会话索引移除
        String userSessionsKey = KEY_USER_SESSIONS_PREFIX + userId + ":sessions";
        redisTemplate.opsForZSet().remove(userSessionsKey, sessionId);
    }

    /**
     * 入队待 MCP 同步的消息（异步重试队列）
     *
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @param role      角色
     * @param content   内容
     */
    public void enqueueMcpSync(String sessionId, String messageId, String role, String content) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> item = new HashMap<>();
        item.put("messageId", messageId);
        item.put("sessionId", sessionId);
        item.put("role", role);
        item.put("content", content);
        item.put("retryCount", 0);
        item.put("timestamp", Instant.now().toEpochMilli());

        // 序列化为 JSON 字符串入队
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(item);
        redisTemplate.opsForList().rightPush(KEY_MCP_RETRY_QUEUE, json);
    }

    /**
     * 获取 MCP 同步重试队列头部消息（供 Worker 消费）
     *
     * @return JSON 字符串消息，null 表示队列为空
     */
    public String pollMcpSyncQueue() {
        return (String) redisTemplate.opsForList().leftPop(KEY_MCP_RETRY_QUEUE, 1, TimeUnit.SECONDS);
    }

    /**
     * 记录 MCP 同步状态（用于去重和重试控制）
     *
     * @param messageId  消息 ID
     * @param retryCount 重试次数
     * @param lastError  最后一次错误信息
     */
    public void recordMcpSyncStatus(String messageId, int retryCount, String lastError) {
        String key = KEY_MCP_SYNC_STATUS_PREFIX + messageId;
        Map<String, Object> status = new HashMap<>();
        status.put("retryCount", retryCount);
        status.put("lastError", lastError);
        if (lastError == null || lastError.isEmpty()) {
            status.put("lastSuccessAt", Instant.now().toEpochMilli());
        }
        redisTemplate.opsForHash().putAll(key, status);
        // 同步状态保留 90 天
        redisTemplate.expire(key, 90L * 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取会话总数（用于统计）
     *
     * @param userId 用户 ID
     * @return 会话数量
     */
    public long getSessionCount(String userId) {
        String userSessionsKey = KEY_USER_SESSIONS_PREFIX + userId + ":sessions";
        Long count = redisTemplate.opsForZSet().size(userSessionsKey);
        return count == null ? 0 : count;
    }

    /**
     * 重新入队消息（带延迟）
     * 注：当前实现为立即入队，实际生产环境可使用 Redis Sorted Set 实现延时队列
     *
     * @param json 消息 JSON 字符串
     */
    public void requeueWithDelay(String json) {
        redisTemplate.opsForList().rightPush(KEY_MCP_RETRY_QUEUE, json);
    }
}
