package com.example.oaticket.controller;

import com.example.oaticket.client.LlmClient;
import com.example.oaticket.model.ChatResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型对话接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final LlmClient llmClient;

    public ChatController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * POST /api/chat
     * 发送一条消息给大模型并返回回复
     *
     * @param request 请求体，只有一个 message 字段
     * @return 回复正文、token 用量与耗时
     */
    @PostMapping
    public ChatResult chat(@RequestBody ChatApiRequest request) {
        return llmClient.chat(request.message());
    }

    /**
     * 对话请求体
     */
    public record ChatApiRequest(String message) {
    }
}
