package com.lww.medical.controller;

import com.lww.medical.dto.SessionDetailDTO;
import com.lww.medical.dto.SessionSummaryDTO;
import com.lww.medical.service.RedisMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史对话控制器
 * 提供会话历史查询 API
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    @Autowired
    private RedisMemoryService redisMemoryService;

    /**
     * 获取用户的会话列表（按最后活跃时间倒序）
     *
     * @param userId 用户 ID（可选，优先从认证信息获取）
     * @return 会话摘要列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionSummaryDTO>> getSessions(
            @RequestParam(required = false) String userId) {

        // 优先从认证信息获取 userId
        String effectiveUserId = getUserIdFromAuth();

        // 如果认证信息中没有，使用传入的参数
        if (effectiveUserId == null || "anonymousUser".equals(effectiveUserId)) {
            effectiveUserId = userId != null ? userId : "default_user";
        }

        log.debug("获取会话列表, userId: {}", effectiveUserId);

        List<SessionSummaryDTO> sessions = redisMemoryService.getSessions(effectiveUserId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 获取会话详情（包含消息列表）
     *
     * @param sessionId 会话 ID
     * @param limit     每页数量（默认 20）
     * @param offset    偏移量（默认 0）
     * @return 会话详情
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionDetailDTO> getSessionDetail(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        SessionDetailDTO detail = redisMemoryService.getSessionDetail(sessionId, limit, offset);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    /**
     * 软删除会话（从列表隐藏，数据保留）
     *
     * @param sessionId 会话 ID
     * @return 204 No Content
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> softDeleteSession(@PathVariable String sessionId) {
        redisMemoryService.softDeleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取用户会话总数
     *
     * @param userId 用户 ID（可选，优先从认证信息获取）
     * @return 会话数量
     */
    @GetMapping("/sessions/count")
    public ResponseEntity<Long> getSessionCount(
            @RequestParam(required = false) String userId) {

        // 优先从认证信息获取 userId
        String effectiveUserId = getUserIdFromAuth();

        if (effectiveUserId == null || "anonymousUser".equals(effectiveUserId)) {
            effectiveUserId = userId != null ? userId : "default_user";
        }

        long count = redisMemoryService.getSessionCount(effectiveUserId);
        return ResponseEntity.ok(count);
    }

    /**
     * 从 SecurityContextHolder 获取当前用户 ID
     */
    private String getUserIdFromAuth() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("获取认证信息失败: {}", e.getMessage());
        }
        return null;
    }
}
