package com.lww.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub 用户信息
 */
@Data
public class GitHubUserInfo {
    private Long id;
    private String login;
    private String name;
    private String email;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    private String bio;
    private String location;

    // 错误信息（如果有）
    private String message;
}
