package com.lww.medical.dto;

/**
 * 会话摘要 DTO
 * 用于列表展示
 */
public class SessionSummaryDTO {
    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 会话标题（首条消息前 8 个字）
     */
    private String title;

    /**
     * 消息总数
     */
    private Long messageCount;

    /**
     * 最后活跃时间戳
     */
    private Long lastActiveAt;

    /**
     * 创建时间戳
     */
    private Long createdAt;

    public SessionSummaryDTO() {
    }

    public SessionSummaryDTO(String sessionId, String title, Long messageCount, Long lastActiveAt, Long createdAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.messageCount = messageCount;
        this.lastActiveAt = lastActiveAt;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Long messageCount) {
        this.messageCount = messageCount;
    }

    public Long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
