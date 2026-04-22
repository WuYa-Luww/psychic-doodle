package com.lww.user.repository;

import com.lww.user.entity.UserOAuthConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户 OAuth 连接 Repository
 */
@Repository
public interface UserOAuthConnectionRepository extends JpaRepository<UserOAuthConnection, Long> {

    /**
     * 根据提供商和用户唯一标识查找
     */
    Optional<UserOAuthConnection> findByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * 根据用户 ID 和提供商查找
     */
    Optional<UserOAuthConnection> findByUserIdAndProvider(Long userId, String provider);

    /**
     * 根据用户 ID 查找所有连接
     */
    List<UserOAuthConnection> findByUserId(Long userId);

    /**
     * 删除用户的某个提供商连接
     */
    void deleteByUserIdAndProvider(Long userId, String provider);

    /**
     * 检查用户是否已绑定某个提供商
     */
    boolean existsByUserIdAndProvider(Long userId, String provider);

    /**
     * 根据提供商和 UnionID 查找（微信跨应用）
     */
    Optional<UserOAuthConnection> findByProviderAndUnionId(String provider, String unionId);
}
