package com.example.oaticket.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 大模型对话请求体，字段名对应 OpenAI 兼容协议
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<Message> messages,
        List<ToolDefinition> tools,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        @JsonProperty("enable_thinking") Boolean enableThinking,
        Boolean stream) {

    /**
     * 单条对话消息
     *
     * 三种形态共用一个结构：普通对话只填 role 和 content，
     * assistant 请求调工具时填 toolCalls，tool 回传结果时填 toolCallId
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls,
            @JsonProperty("tool_call_id") String toolCallId) {

        /**
         * 构造一条系统提示
         *
         * @param content 提示内容
         * @return 角色为 system 的消息
         */
        public static Message system(String content) {
            return new Message("system", content, null, null);
        }

        /**
         * 构造一条用户消息
         *
         * @param content 用户输入内容
         * @return 角色为 user 的消息
         */
        public static Message user(String content) {
            return new Message("user", content, null, null);
        }

        /**
         * 构造一条助手消息
         *
         * 必须把模型返回的工具调用原样带回上下文，否则下一次请求里 tool 消息找不到
         * 对应的 tool_call_id，服务端会直接报错
         *
         * @param content 正文，模型只请求调工具时为空
         * @param toolCalls 模型请求调用的工具
         * @return 角色为 assistant 的消息
         */
        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, toolCalls, null);
        }

        /**
         * 构造一条工具结果消息
         *
         * @param toolCallId 对应的调用 ID，必须与模型返回的完全一致
         * @param content 工具执行结果的 JSON 字符串
         * @return 角色为 tool 的消息
         */
        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, null, toolCallId);
        }
    }
}
