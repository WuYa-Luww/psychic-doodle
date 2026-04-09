package com.lww.user.dto;

import java.time.LocalDateTime;

/**
 * 用户响应 DTO
 */
public class UserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatar;
    private LocalDateTime createdAt;

    public UserResponse() {}

    public UserResponse(Long id, String username, String nickname, String phone, String avatar, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.phone = phone;
        this.avatar = avatar;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
