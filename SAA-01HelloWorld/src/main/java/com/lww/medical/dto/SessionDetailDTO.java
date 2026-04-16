package com.lww.medical.dto;

import java.util.List;

/**
 * 会话详情 DTO
 * 包含会话元数据和消息列表
 */
public class SessionDetailDTO {
    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间戳
     */
    private Long createdAt;

    /**
     * 最后活跃时间戳
     */
    private Long lastActiveAt;

    /**
     * 消息总数
     */
    private Long messageCount;

    /**
     * 消息列表
     */
    private List<MessageDTO> messages;

    public SessionDetailDTO() {
    }

    public SessionDetailDTO(String sessionId, String title, Long createdAt, Long lastActiveAt, Long messageCount, List<MessageDTO> messages) {
        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.messageCount = messageCount;
        this.messages = messages;
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

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Long messageCount) {
        this.messageCount = messageCount;
    }

    public List<MessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }
}
