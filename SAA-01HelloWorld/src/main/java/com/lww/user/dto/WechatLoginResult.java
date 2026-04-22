package com.lww.user.dto;

/**
 * 微信登录结果 DTO
 */
public class WechatLoginResult {

    private boolean success;
    private String token;        // JWT token
    private String error;        // 错误信息
    private UserResponse user;   // 用户信息

    public WechatLoginResult() {
    }

    public WechatLoginResult(boolean success, String token, String error, UserResponse user) {
        this.success = success;
        this.token = token;
        this.error = error;
        this.user = user;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }
}
