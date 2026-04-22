package com.lww.user.controller;

import com.lww.user.dto.WechatBindRequest;
import com.lww.user.dto.WechatLoginResult;
import com.lww.user.dto.WechatQrCodeResponse;
import com.lww.user.entity.User;
import com.lww.user.entity.UserOAuthConnection;
import com.lww.user.service.UserService;
import com.lww.user.service.WechatOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 微信登录控制器
 */
@RestController
@RequestMapping("/api/auth/wechat")
public class WechatController {

    private static final Logger logger = LoggerFactory.getLogger(WechatController.class);

    @Autowired
    private WechatOAuthService wechatService;

    @Autowired
    private UserService userService;

    /**
     * 获取微信扫码登录二维码 URL
     */
    @GetMapping("/qrcode")
    public ResponseEntity<?> getQrCode() {
        try {
            WechatQrCodeResponse response = wechatService.generateQrCodeUrl();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取微信二维码失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "获取微信二维码失败");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 微信回调端点
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {

        logger.info("微信回调: code={}, state={}", code, state);

        try {
            // 处理回调
            wechatService.handleCallback(code, state);

            // 重定向到登录页，携带 state 用于轮询
            response.sendRedirect("/login.html?wechat_state=" + state);
        } catch (Exception e) {
            logger.error("微信回调处理失败", e);
            // 重定向到登录页并显示错误
            response.sendRedirect("/login.html?wechat_error=" + encodeUrl(e.getMessage()));
        }
    }

    /**
     * 轮询检查登录状态
     */
    @GetMapping("/poll")
    public ResponseEntity<?> pollLoginStatus(@RequestParam String state) {
        WechatLoginResult result = wechatService.checkLoginStatus(state);

        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "waiting"));
        }

        if (result.isSuccess()) {
            wechatService.cleanupState(state);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "token", result.getToken(),
                    "user", result.getUser()
            ));
        } else {
            wechatService.cleanupState(state);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "error", result.getError()
            ));
        }
    }

    /**
     * 绑定微信（需登录）
     */
    @PostMapping("/bind")
    public ResponseEntity<?> bindWechat(
            @RequestBody WechatBindRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            // 获取当前用户 ID（从 UserDetails 中解析）
            Long userId = getCurrentUserId(userDetails);
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "无法获取用户信息"));
            }

            wechatService.bindWechat(userId, request.getCode());
            return ResponseEntity.ok(Map.of("message", "绑定成功"));
        } catch (RuntimeException e) {
            logger.error("绑定微信失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 解绑微信（需登录）
     */
    @DeleteMapping("/unbind")
    public ResponseEntity<?> unbindWechat(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            Long userId = getCurrentUserId(userDetails);
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "无法获取用户信息"));
            }

            wechatService.unbindWechat(userId);
            return ResponseEntity.ok(Map.of("message", "解绑成功"));
        } catch (Exception e) {
            logger.error("解绑微信失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "解绑失败"));
        }
    }

    /**
     * 检查微信绑定状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> getBindStatus(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            Long userId = getCurrentUserId(userDetails);
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "无法获取用户信息"));
            }

            boolean bound = wechatService.isWechatBound(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("bound", bound);

            if (bound) {
                Optional<UserOAuthConnection> conn = wechatService.getWechatConnection(userId);
                conn.ifPresent(c -> {
                    result.put("nickname", c.getNickname());
                    result.put("avatarUrl", c.getAvatarUrl());
                });
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取绑定状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取状态失败"));
        }
    }

    // ========== Helper Methods ==========

    /**
     * 从 UserDetails 获取用户 ID
     */
    private Long getCurrentUserId(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        // 从 UserDetails 获取用户名，然后查询用户 ID
        String username = userDetails.getUsername();
        User user = userService.findByUsername(username);
        return user != null ? user.getId() : null;
    }

    /**
     * URL 编码
     */
    private String encodeUrl(String str) {
        if (str == null) return "";
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
