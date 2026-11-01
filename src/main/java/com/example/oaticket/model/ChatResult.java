package com.example.oaticket.model;

/**
 * 一次对话的调用结果
 *
 * 除了正文，还带上 token 用量和耗时：后面做成本核算、延迟统计都要靠这两个数字
 */
public record ChatResult(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long elapsedMillis) {
}
