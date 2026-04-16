package com.lww.medical.controller;

import com.lww.medical.dto.SessionDetailDTO;
import com.lww.medical.dto.SessionSummaryDTO;
import com.lww.medical.service.RedisMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史对话控制器
 * 提供会话历史查询 API
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    @Autowired
    private RedisMemoryService redisMemoryService;

    /**
     * 获取用户的会话列表（按最后活跃时间倒序）
     *
     * @param userId 用户 ID
     * @return 会话摘要列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionSummaryDTO>> getSessions(@RequestParam String userId) {
        List<SessionSummaryDTO> sessions = redisMemoryService.getSessions(userId);
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
     * @param userId 用户 ID
     * @return 会话数量
     */
    @GetMapping("/sessions/count")
    public ResponseEntity<Long> getSessionCount(@RequestParam String userId) {
        long count = redisMemoryService.getSessionCount(userId);
        return ResponseEntity.ok(count);
    }
}
