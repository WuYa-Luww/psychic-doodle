package com.lww.user.controller;

import com.lww.user.dto.GitHubLoginResult;
import com.lww.user.service.GitHubOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * GitHub OAuth 控制器
 */
@RestController
@RequestMapping("/api/auth/github")
public class GitHubController {

    @Autowired
    private GitHubOAuthService gitHubService;

    /**
     * 发起 GitHub 登录（重定向模式）
     */
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String state = gitHubService.generateState();
        String authorizeUrl = gitHubService.generateAuthorizeUrl(state);
        response.sendRedirect(authorizeUrl);
    }

    /**
     * GitHub 回调端点
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {

        gitHubService.handleCallback(code, state);

        // 重定向到登录页，携带 state 用于轮询
        response.sendRedirect("/login.html?github_state=" + state);
    }

    /**
     * 轮询检查登录状态
     */
    @GetMapping("/poll")
    public ResponseEntity<?> pollLoginStatus(@RequestParam String state) {
        GitHubLoginResult result = gitHubService.checkLoginStatus(state);

        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "waiting"));
        }

        if (result.isSuccess()) {
            gitHubService.cleanupState(state);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "token", result.getToken(),
                    "user", result.getUser()
            ));
        } else {
            gitHubService.cleanupState(state);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "error", result.getError()
            ));
        }
    }
}
