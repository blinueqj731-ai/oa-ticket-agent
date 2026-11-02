package com.example.oaticket.model;

import java.util.List;

/**
 * Agent 处理结果
 *
 * 除了最终回复，还带上中途调用过的工具，便于排查"它为什么这么说"
 */
public record AgentResult(
        String content,
        List<ToolInvocation> toolInvocations,
        int totalTokens) {

    /**
     * 一次工具调用的记录
     */
    public record ToolInvocation(String toolName, String arguments, String result) {
    }
}
