package com.lww.medical.dto;

/**
 * 消息 DTO
 * 用于传输消息内容
 */
public class MessageDTO {
    /**
     * 消息 ID（格式: {sessionId}:{sequence}）
     */
    private String messageId;

    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息时间戳
     */
    private Long timestamp;

    /**
     * 在会话中的序号
     */
    private Long sequence;

    public MessageDTO() {
    }

    public MessageDTO(String messageId, String role, String content, Long timestamp, Long sequence) {
        this.messageId = messageId;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.sequence = sequence;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }
}
