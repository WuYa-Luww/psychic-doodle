package com.lww.medical.controller;

import com.lww.medical.service.RedisMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据迁移控制器
 * 用于将旧用户的历史记录迁移到新用户
 */
@RestController
@RequestMapping("/api/migration")
public class DataMigrationController {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationController.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMemoryService redisMemoryService;

    private static final String KEY_USER_SESSIONS_PREFIX = "chat:user:";
    private static final String KEY_SESSION_PREFIX = "chat:session:";

    /**
     * 列出所有有历史记录的用户
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsersWithSessions() {
        Set<String> userKeys = redisTemplate.keys(KEY_USER_SESSIONS_PREFIX + "*:sessions");
        if (userKeys == null || userKeys.isEmpty()) {
            return ResponseEntity.ok(Collections.singletonMap("message", "没有找到任何历史记录"));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : userKeys) {
            String userId = key.replace(KEY_USER_SESSIONS_PREFIX, "").replace(":sessions", "");
            Long sessionCount = redisTemplate.opsForZSet().size(key);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", userId);
            userInfo.put("sessionCount", sessionCount != null ? sessionCount : 0);
            result.add(userInfo);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 将旧用户的历史记录迁移到新用户
     *
     * @param oldUserId 旧用户ID（用户名）
     * @param newUserId 新用户ID（用户名）
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transferSessions(
            @RequestParam String oldUserId,
            @RequestParam String newUserId) {

        String oldUserSessionsKey = KEY_USER_SESSIONS_PREFIX + oldUserId + ":sessions";
        String newUserSessionsKey = KEY_USER_SESSIONS_PREFIX + newUserId + ":sessions";

        // 检查旧用户是否有数据
        Long oldCount = redisTemplate.opsForZSet().size(oldUserSessionsKey);
        if (oldCount == null || oldCount == 0) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "源用户没有历史记录: " + oldUserId));
        }

        // 获取旧用户的所有会话
        Set<Object> sessionIds = redisTemplate.opsForZSet().range(oldUserSessionsKey, 0, -1);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "没有找到可迁移的会话"));
        }

        int migratedCount = 0;
        for (Object sessionIdObj : sessionIds) {
            String sessionId = sessionIdObj.toString();

            // 更新会话元数据中的 userId
            redisTemplate.opsForHash().put(KEY_SESSION_PREFIX + sessionId, "userId", newUserId);

            // 获取会话的最后活跃时间作为 score
            Object lastActiveObj = redisTemplate.opsForHash().get(KEY_SESSION_PREFIX + sessionId, "lastActiveAt");
            double score = lastActiveObj != null ? ((Number) lastActiveObj).doubleValue() : System.currentTimeMillis();

            // 添加到新用户的会话索引
            redisTemplate.opsForZSet().add(newUserSessionsKey, sessionId, score);
        }

        migratedCount = sessionIds.size();

        // 删除旧用户的索引（保留会话数据，只删除索引）
        redisTemplate.delete(oldUserSessionsKey);

        log.info("数据迁移完成: {} -> {}, 共 {} 个会话", oldUserId, newUserId, migratedCount);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "迁移成功");
        result.put("oldUserId", oldUserId);
        result.put("newUserId", newUserId);
        result.put("migratedSessions", migratedCount);

        return ResponseEntity.ok(result);
    }

    /**
     * 合并两个用户的历史记录（不删除旧数据，只复制）
     */
    @PostMapping("/merge")
    public ResponseEntity<?> mergeSessions(
            @RequestParam String sourceUserId,
            @RequestParam String targetUserId) {

        String sourceKey = KEY_USER_SESSIONS_PREFIX + sourceUserId + ":sessions";
        String targetKey = KEY_USER_SESSIONS_PREFIX + targetUserId + ":sessions";

        Set<Object> sessionIds = redisTemplate.opsForZSet().range(sourceKey, 0, -1);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "源用户没有历史记录"));
        }

        int mergedCount = 0;
        for (Object sessionIdObj : sessionIds) {
            String sessionId = sessionIdObj.toString();

            // 检查目标用户是否已有此会话
            Double existingScore = redisTemplate.opsForZSet().score(targetKey, sessionId);
            if (existingScore != null) {
                continue; // 已存在，跳过
            }

            // 更新会话元数据中的 userId
            redisTemplate.opsForHash().put(KEY_SESSION_PREFIX + sessionId, "userId", targetUserId);

            // 获取会话的最后活跃时间作为 score
            Object lastActiveObj = redisTemplate.opsForHash().get(KEY_SESSION_PREFIX + sessionId, "lastActiveAt");
            double score = lastActiveObj != null ? ((Number) lastActiveObj).doubleValue() : System.currentTimeMillis();

            // 添加到目标用户的会话索引
            redisTemplate.opsForZSet().add(targetKey, sessionId, score);
            mergedCount++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "合并完成");
        result.put("sourceUserId", sourceUserId);
        result.put("targetUserId", targetUserId);
        result.put("mergedSessions", mergedCount);

        return ResponseEntity.ok(result);
    }
}
