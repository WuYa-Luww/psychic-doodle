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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 OAuth 服务
 */
@Service
public class WechatOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(WechatOAuthService.class);

    private static final String PROVIDER_WECHAT = "wechat";

    // 微信 API URLs
    private static final String WECHAT_AUTH_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String WECHAT_USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    @Value("${wechat.open.app-id:}")
    private String appId;

    @Value("${wechat.open.app-secret:}")
    private String appSecret;

    @Value("${wechat.open.redirect-uri:http://localhost:8002/api/auth/wechat/callback}")
    private String redirectUri;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOAuthConnectionRepository oauthRepository;

    @Autowired
    private JwtUtils jwtUtils;

    // 存储 state -> 登录结果（用于轮询）
    private final Map<String, WechatLoginResult> loginResultCache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成微信扫码登录 URL
     */
    public WechatQrCodeResponse generateQrCodeUrl() {
        String state = UUID.randomUUID().toString().replace("-", "");

        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String qrCodeUrl = String.format(
                "%s?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_login&state=%s#wechat_redirect",
                WECHAT_AUTH_URL,
                appId,
                encodedRedirectUri,
                state
        );

        return new WechatQrCodeResponse(qrCodeUrl, state);
    }

    /**
     * 处理微信回调
     */
    public WechatLoginResult handleCallback(String code, String state) {
        try {
            // 1. 用 code 换取 access_token
            WechatAccessTokenResponse tokenResp = getAccessToken(code);
            if (tokenResp.hasError()) {
                logger.error("微信获取 access_token 失败: {} - {}", tokenResp.getErrcode(), tokenResp.getErrmsg());
                WechatLoginResult result = new WechatLoginResult(false, null,
                        "微信授权失败: " + tokenResp.getErrmsg(), null);
                loginResultCache.put(state, result);
                return result;
            }

            // 2. 获取用户信息
            WechatUserInfo userInfo = getUserInfo(tokenResp.getAccessToken(), tokenResp.getOpenid());
            if (userInfo.hasError()) {
                logger.error("微信获取用户信息失败: {} - {}", userInfo.getErrcode(), userInfo.getErrmsg());
                WechatLoginResult result = new WechatLoginResult(false, null,
                        "获取用户信息失败: " + userInfo.getErrmsg(), null);
                loginResultCache.put(state, result);
                return result;
            }

            // 3. 查找或创建用户
            User user = findOrCreateUser(userInfo, tokenResp);

            // 4. 生成 JWT
            String jwtToken = jwtUtils.generateTokenFromUsername(user.getUsername());

            // 5. 返回成功结果
            WechatLoginResult result = new WechatLoginResult(
                    true,
                    jwtToken,
                    null,
                    toUserResponse(user)
            );
            loginResultCache.put(state, result);

            logger.info("微信登录成功: openid={}, username={}", tokenResp.getOpenid(), user.getUsername());
            return result;

        } catch (Exception e) {
            logger.error("微信登录处理异常", e);
            WechatLoginResult result = new WechatLoginResult(false, null,
                    "登录失败: " + e.getMessage(), null);
            loginResultCache.put(state, result);
            return result;
        }
    }

    /**
     * 轮询检查登录状态
     */
    public WechatLoginResult checkLoginStatus(String state) {
        return loginResultCache.get(state);
    }

    /**
     * 清理登录状态
     */
    public void cleanupState(String state) {
        loginResultCache.remove(state);
    }

    /**
     * 绑定微信账号（已登录用户）
     */
    public void bindWechat(Long userId, String code) {
        // 获取 access_token
        WechatAccessTokenResponse tokenResp = getAccessToken(code);
        if (tokenResp.hasError()) {
            throw new RuntimeException("微信授权失败: " + tokenResp.getErrmsg());
        }

        // 检查是否已被其他用户绑定
        Optional<UserOAuthConnection> existingConn = oauthRepository.findByProviderAndProviderUserId(
                PROVIDER_WECHAT, tokenResp.getOpenid());
        if (existingConn.isPresent() && !existingConn.get().getUser().getId().equals(userId)) {
            throw new RuntimeException("该微信已被其他账号绑定");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 获取微信用户信息
        WechatUserInfo userInfo = getUserInfo(tokenResp.getAccessToken(), tokenResp.getOpenid());

        // 创建或更新 OAuth 连接
        UserOAuthConnection connection = existingConn.orElse(new UserOAuthConnection());
        connection.setUser(user);
        connection.setProvider(PROVIDER_WECHAT);
        connection.setProviderUserId(tokenResp.getOpenid());
        connection.setUnionId(tokenResp.getUnionid());
        connection.setAccessToken(tokenResp.getAccessToken());
        connection.setRefreshToken(tokenResp.getRefreshToken());
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenResp.getExpiresIn()));
        connection.setNickname(userInfo.getNickname());
        connection.setAvatarUrl(userInfo.getHeadimgurl());

        oauthRepository.save(connection);

        // 更新用户头像（如果用户没有头像）
        if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
            user.setAvatar(userInfo.getHeadimgurl());
            userRepository.save(user);
        }
    }

    /**
     * 解绑微信账号
     */
    public void unbindWechat(Long userId) {
        oauthRepository.deleteByUserIdAndProvider(userId, PROVIDER_WECHAT);
    }

    /**
     * 检查是否已绑定微信
     */
    public boolean isWechatBound(Long userId) {
        return oauthRepository.existsByUserIdAndProvider(userId, PROVIDER_WECHAT);
    }

    /**
     * 获取用户的微信绑定信息
     */
    public Optional<UserOAuthConnection> getWechatConnection(Long userId) {
        return oauthRepository.findByUserIdAndProvider(userId, PROVIDER_WECHAT);
    }

    // ========== Private Methods ==========

    /**
     * 用 code 换取 access_token
     */
    private WechatAccessTokenResponse getAccessToken(String code) {
        String url = String.format(
                "%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                WECHAT_TOKEN_URL, appId, appSecret, code
        );

        try {
            String response = restTemplate.getForObject(url, String.class);
            logger.debug("微信 access_token 响应: {}", response);
            return objectMapper.readValue(response, WechatAccessTokenResponse.class);
        } catch (Exception e) {
            logger.error("解析微信 access_token 响应失败", e);
            WechatAccessTokenResponse errorResp = new WechatAccessTokenResponse();
            errorResp.setErrcode(-1);
            errorResp.setErrmsg("网络请求失败");
            return errorResp;
        }
    }

    /**
     * 获取微信用户信息
     */
    private WechatUserInfo getUserInfo(String accessToken, String openid) {
        String url = String.format(
                "%s?access_token=%s&openid=%s",
                WECHAT_USERINFO_URL, accessToken, openid
        );

        try {
            String response = restTemplate.getForObject(url, String.class);
            logger.debug("微信用户信息响应: {}", response);
            return objectMapper.readValue(response, WechatUserInfo.class);
        } catch (Exception e) {
            logger.error("解析微信用户信息响应失败", e);
            WechatUserInfo errorResp = new WechatUserInfo();
            errorResp.setErrcode(-1);
            errorResp.setErrmsg("获取用户信息失败");
            return errorResp;
        }
    }

    /**
     * 查找或创建用户
     */
    private User findOrCreateUser(WechatUserInfo userInfo, WechatAccessTokenResponse tokenResp) {
        // 1. 先通过 unionid 查找（如果有）
        if (tokenResp.getUnionid() != null && !tokenResp.getUnionid().isEmpty()) {
            Optional<UserOAuthConnection> conn = oauthRepository.findByProviderAndUnionId(
                    PROVIDER_WECHAT, tokenResp.getUnionid());
            if (conn.isPresent()) {
                return conn.get().getUser();
            }
        }

        // 2. 通过 openid 查找
        Optional<UserOAuthConnection> conn = oauthRepository.findByProviderAndProviderUserId(
                PROVIDER_WECHAT, tokenResp.getOpenid());
        if (conn.isPresent()) {
            User user = conn.get().getUser();
            // 更新 token 信息
            updateOAuthConnection(conn.get(), userInfo, tokenResp);
            return user;
        }

        // 3. 创建新用户
        User user = new User();
        String username = "wx_" + tokenResp.getOpenid().substring(0, Math.min(12, tokenResp.getOpenid().length()));

        // 确保用户名唯一
        int suffix = 1;
        String originalUsername = username;
        while (userRepository.existsByUsername(username)) {
            username = originalUsername + "_" + suffix;
            suffix++;
        }

        user.setUsername(username);
        user.setNickname(userInfo.getNickname() != null ? userInfo.getNickname() : "微信用户");
        user.setAvatar(userInfo.getHeadimgurl());
        user.setPassword(null); // 微信用户无密码
        user.setEnabled(true);

        user = userRepository.save(user);

        // 4. 创建 OAuth 连接
        UserOAuthConnection connection = new UserOAuthConnection();
        connection.setUser(user);
        connection.setProvider(PROVIDER_WECHAT);
        connection.setProviderUserId(tokenResp.getOpenid());
        connection.setUnionId(tokenResp.getUnionid());
        connection.setAccessToken(tokenResp.getAccessToken());
        connection.setRefreshToken(tokenResp.getRefreshToken());
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenResp.getExpiresIn()));
        connection.setNickname(userInfo.getNickname());
        connection.setAvatarUrl(userInfo.getHeadimgurl());

        oauthRepository.save(connection);

        return user;
    }

    /**
     * 更新 OAuth 连接信息
     */
    private void updateOAuthConnection(UserOAuthConnection connection, WechatUserInfo userInfo,
                                        WechatAccessTokenResponse tokenResp) {
        connection.setAccessToken(tokenResp.getAccessToken());
        connection.setRefreshToken(tokenResp.getRefreshToken());
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenResp.getExpiresIn()));
        if (userInfo.getNickname() != null) {
            connection.setNickname(userInfo.getNickname());
        }
        if (userInfo.getHeadimgurl() != null) {
            connection.setAvatarUrl(userInfo.getHeadimgurl());
        }
        oauthRepository.save(connection);
    }

    /**
     * 转换为 UserResponse
     */
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
