package com.lww.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub 登录结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GitHubLoginResult {
    private boolean success;
    private String token;
    private String error;
    private UserResponse user;
}
