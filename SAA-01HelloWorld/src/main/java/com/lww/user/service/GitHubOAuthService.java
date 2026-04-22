package com.lww.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lww.config.JwtUtils;
import com.lww.user.dto.*;
import com.lww.user.entity.User;
import com.lww.user.entity.UserOAuthConnection;
import com.lww.user.repository.UserOAuthConnectionRepository;
import com.lww.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitHub OAuth 服务
 */
@Service
public class GitHubOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOAuthService.class);
    private static final String PROVIDER_GITHUB = "github";

    // GitHub API URLs
    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    @Value("${github.redirect-uri:http://localhost:8002/api/auth/github/callback}")
    private String redirectUri;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOAuthConnectionRepository oauthRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private final Map<String, GitHubLoginResult> loginResultCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成 GitHub 授权 URL（直接跳转模式）
     */
    public String generateAuthorizeUrl(String state) {
        try {
            String encodedRedirectUri = java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8);
            return String.format("%s?client_id=%s&redirect_uri=%s&scope=user:email&state=%s",
                    GITHUB_AUTHORIZE_URL, clientId, encodedRedirectUri, state);
        } catch (Exception e) {
            logger.error("生成授权 URL 失败", e);
            return null;
        }
    }

    /**
     * 生成授权 state
     */
    public String generateState() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 处理 GitHub 回调
     */
    public GitHubLoginResult handleCallback(String code, String state) {
        try {
            // 1. 用 code 换取 access_token
            GitHubAccessTokenResponse tokenResp = getAccessToken(code);
            if (tokenResp.getError() != null) {
                logger.error("GitHub 获取 access_token 失败: {}", tokenResp.getErrorDescription());
                GitHubLoginResult result = new GitHubLoginResult(false, null,
                        "GitHub 授权失败: " + tokenResp.getErrorDescription(), null);
                loginResultCache.put(state, result);
                return result;
            }

            // 2. 获取用户信息
            GitHubUserInfo userInfo = getUserInfo(tokenResp.getAccessToken());
            if (userInfo.getMessage() != null) {
                logger.error("GitHub 获取用户信息失败: {}", userInfo.getMessage());
                GitHubLoginResult result = new GitHubLoginResult(false, null,
                        "获取用户信息失败: " + userInfo.getMessage(), null);
                loginResultCache.put(state, result);
                return result;
            }

            // 3. 查找或创建用户
            User user = findOrCreateUser(userInfo, tokenResp.getAccessToken());

            // 4. 生成 JWT
            String jwtToken = jwtUtils.generateTokenFromUsername(user.getUsername());

            GitHubLoginResult result = new GitHubLoginResult(true, jwtToken, null, toUserResponse(user));
            loginResultCache.put(state, result);

            logger.info("GitHub 登录成功: githubId={}, username={}", userInfo.getId(), user.getUsername());
            return result;

        } catch (Exception e) {
            logger.error("GitHub 登录处理异常", e);
            GitHubLoginResult result = new GitHubLoginResult(false, null, "登录失败: " + e.getMessage(), null);
            loginResultCache.put(state, result);
            return result;
        }
    }

    /**
     * 轮询检查登录状态
     */
    public GitHubLoginResult checkLoginStatus(String state) {
        return loginResultCache.get(state);
    }

    /**
     * 清理登录状态
     */
    public void cleanupState(String state) {
        loginResultCache.remove(state);
    }

    // ========== Private Methods ==========

    private GitHubAccessTokenResponse getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(GITHUB_TOKEN_URL, request, String.class);

        try {
            return objectMapper.readValue(response.getBody(), GitHubAccessTokenResponse.class);
        } catch (Exception e) {
            logger.error("解析 GitHub access_token 响应失败", e);
            GitHubAccessTokenResponse errorResp = new GitHubAccessTokenResponse();
            errorResp.setError("parse_error");
            errorResp.setErrorDescription("响应解析失败");
            return errorResp;
        }
    }

    private GitHubUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(GITHUB_USER_URL, HttpMethod.GET, request, String.class);

        try {
            return objectMapper.readValue(response.getBody(), GitHubUserInfo.class);
        } catch (Exception e) {
            logger.error("解析 GitHub 用户信息响应失败", e);
            GitHubUserInfo errorResp = new GitHubUserInfo();
            errorResp.setMessage("响应解析失败");
            return errorResp;
        }
    }

    private User findOrCreateUser(GitHubUserInfo userInfo, String accessToken) {
        String githubId = String.valueOf(userInfo.getId());

        // 1. 通过 GitHub ID 查找
        Optional<UserOAuthConnection> conn = oauthRepository.findByProviderAndProviderUserId(
                PROVIDER_GITHUB, githubId);
        if (conn.isPresent()) {
            User user = conn.get().getUser();
            // 更新 token
            updateOAuthConnection(conn.get(), userInfo, accessToken);
            return user;
        }

        // 2. 创建新用户
        User user = new User();
        String username = "gh_" + userInfo.getLogin();

        // 确保用户名唯一
        int suffix = 1;
        String originalUsername = username;
        while (userRepository.existsByUsername(username)) {
            username = originalUsername + "_" + suffix;
            suffix++;
        }

        user.setUsername(username);
        user.setNickname(userInfo.getName() != null ? userInfo.getName() : userInfo.getLogin());
        user.setAvatar(userInfo.getAvatarUrl());
        user.setPassword(null);
        user.setEnabled(true);

        user = userRepository.save(user);

        // 3. 创建 OAuth 连接
        UserOAuthConnection connection = new UserOAuthConnection();
        connection.setUser(user);
        connection.setProvider(PROVIDER_GITHUB);
        connection.setProviderUserId(githubId);
        connection.setAccessToken(accessToken);
        connection.setNickname(userInfo.getLogin());
        connection.setAvatarUrl(userInfo.getAvatarUrl());

        oauthRepository.save(connection);

        return user;
    }

    private void updateOAuthConnection(UserOAuthConnection connection, GitHubUserInfo userInfo, String accessToken) {
        connection.setAccessToken(accessToken);
        if (userInfo.getLogin() != null) {
            connection.setNickname(userInfo.getLogin());
        }
        if (userInfo.getAvatarUrl() != null) {
            connection.setAvatarUrl(userInfo.getAvatarUrl());
        }
        oauthRepository.save(connection);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getAvatar(),
                user.getCreatedAt()
        );
    }
}
