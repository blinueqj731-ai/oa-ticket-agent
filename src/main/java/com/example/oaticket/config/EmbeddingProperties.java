package com.example.oaticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量检索参数
 */
@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(
        String model,
        int topK,
        double minScore) {
}
