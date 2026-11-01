package com.example.oaticket.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 大模型对话请求体，字段名对应 OpenAI 兼容协议
 */
public record ChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        @JsonProperty("enable_thinking") Boolean enableThinking,
        Boolean stream) {

    /**
     * 单条对话消息
     */
    public record Message(String role, String content) {

        /**
         * 构造一条用户消息
         *
         * @param content 用户输入内容
         * @return 角色为 user 的消息
         */
        public static Message user(String content) {
            return new Message("user", content);
        }
    }
}
