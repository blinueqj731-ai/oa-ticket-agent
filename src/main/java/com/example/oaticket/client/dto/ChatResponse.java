package com.example.oaticket.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 大模型对话响应体，只取用得到的字段
 */
public record ChatResponse(List<Choice> choices, Usage usage) {

    /**
     * 取第一条回复的正文
     *
     * @return 回复正文；没有返回内容时返回 null
     */
    public String content() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).message().content();
    }

    /**
     * 取第一条回复的思考内容
     *
     * @return 思考过程；未开启思考模式时返回 null
     */
    public String reasoningContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).message().reasoningContent();
    }

    /**
     * 单条候选回复
     */
    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    /**
     * 回复消息
     */
    public record Message(
            String role,
            String content,
            @JsonProperty("reasoning_content") String reasoningContent) {
    }

    /**
     * token 用量
     */
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }
}
