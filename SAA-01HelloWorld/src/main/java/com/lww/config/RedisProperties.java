package com.lww.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 配置属性类
 */
@Component
@ConfigurationProperties(prefix = "spring.redis")
public class RedisProperties {

    /**
     * Redis 主机地址
     */
    private String host = "127.0.0.1";

    /**
     * Redis 端口
     */
    private int port = 6379;

    /**
     * Redis 密码（空表示无密码）
     */
    private String password = "";

    /**
     * Redis 数据库索引
     */
    private int database = 0;

    /**
     * 连接超时时间
     */
    private String timeout = "5000ms";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }
}
