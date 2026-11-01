package com.example.oaticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 大模型调用参数
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String apiKey,
        String baseUrl,
        String model,
        int timeoutSeconds) {
}
