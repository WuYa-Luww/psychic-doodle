package com.lww.user.dto;

/**
 * 微信二维码响应 DTO
 */
public class WechatQrCodeResponse {

    private String qrCodeUrl;    // 微信二维码 URL
    private String state;        // 状态码（用于轮询验证）

    public WechatQrCodeResponse() {
    }

    public WechatQrCodeResponse(String qrCodeUrl, String state) {
        this.qrCodeUrl = qrCodeUrl;
        this.state = state;
    }

    public String getQrCodeUrl() {
        return qrCodeUrl;
    }

    public void setQrCodeUrl(String qrCodeUrl) {
        this.qrCodeUrl = qrCodeUrl;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
