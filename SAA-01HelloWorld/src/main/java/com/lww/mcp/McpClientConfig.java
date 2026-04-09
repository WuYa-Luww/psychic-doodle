package com.lww.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP Client 配置
 * 连接外部 MCP 服务器：Memory、Sequential Thinking、Time
 */
@Configuration
public class McpClientConfig {

    @Bean
    public McpSyncClient memoryMcpClient() {
        ServerParameters params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-memory")
                .build();
        return McpClient.sync(new StdioClientTransport(params))
                .requestTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public McpSyncClient sequentialThinkingMcpClient() {
        ServerParameters params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-sequential-thinking")
                .build();
        return McpClient.sync(new StdioClientTransport(params))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public McpSyncClient timeMcpClient() {
        ServerParameters params = ServerParameters.builder("uvx")
                .args("mcp-server-time")
                .build();
        return McpClient.sync(new StdioClientTransport(params))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }
}
