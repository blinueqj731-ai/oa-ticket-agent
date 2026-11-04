package com.example.oaticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 循环的控制参数
 *
 * 三个上限任一触发都会停止循环并降级，不会抛异常
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxSteps,
        int timeoutSeconds,
        int tokenBudget) {
}
