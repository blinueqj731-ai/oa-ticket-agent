package com.example.oaticket.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Agent 处理结果
 *
 * 除了最终回复，还带上中途调用过的工具和结束原因，便于排查"它为什么这么说"和"它是怎么停下来的"
 */
public record AgentResult(
        String content,
        List<ToolInvocation> toolInvocations,
        int totalTokens,
        StopReason stopReason,
        int steps) {

    /**
     * 是否属于被迫中止
     *
     * record 的自定义方法默认不会进 JSON，必须显式标 @JsonProperty
     *
     * @return 正常收敛返回 false，触发任一上限返回 true
     */
    @JsonProperty("degraded")
    public boolean degraded() {
        return stopReason != StopReason.COMPLETED;
    }

    /**
     * 一次工具调用的记录
     */
    public record ToolInvocation(String toolName, String arguments, String result) {
    }
}
