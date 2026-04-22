package com.lww.user.dto;

/**
 * 微信绑定请求 DTO
 */
public class WechatBindRequest {

    private String code;         // 微信授权码

    public WechatBindRequest() {
    }

    public WechatBindRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
