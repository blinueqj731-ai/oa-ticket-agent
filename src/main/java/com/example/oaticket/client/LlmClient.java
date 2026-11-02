package com.example.oaticket.client;

import com.example.oaticket.client.dto.ChatRequest;
import com.example.oaticket.client.dto.ChatResponse;
import com.example.oaticket.client.dto.ToolDefinition;
import com.example.oaticket.config.LlmProperties;
import com.example.oaticket.model.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * 大模型调用客户端
 *
 * 走的是 OpenAI 兼容协议，换成任何支持该协议的模型服务都只需改 base-url 和 model
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final double TEMPERATURE = 0.7;

    private final LlmProperties properties;
    private final RestClient restClient;

    public LlmClient(LlmProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory(properties.timeoutSeconds()))
                .build();
    }

    /**
     * 发送一条用户消息，返回模型回复
     *
     * @param userMessage 用户输入内容
     * @return 回复正文，以及本次调用的 token 用量与耗时
     */
    public ChatResult chat(String userMessage) {
        // 模型面对空输入不会报错，会自己脑补一段内容出来，必须在入口拦住
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        long start = System.currentTimeMillis();
        ChatResponse response = send(List.of(ChatRequest.Message.user(userMessage)), null);
        long elapsed = System.currentTimeMillis() - start;

        if (response.content() == null) {
            // 思考模式下正文可能为空而 reasoning_content 有值，这种情况要单独提示
            if (response.reasoningContent() != null) {
                throw new IllegalStateException("模型返回了思考内容但正文为空，确认请求中已关闭思考模式");
            }
            throw new IllegalStateException("大模型返回内容为空");
        }

        return new ChatResult(response.content(),
                promptTokens(response), completionTokens(response), totalTokens(response), elapsed);
    }

    /**
     * 发送完整的对话上下文，可携带工具定义
     *
     * @param messages 完整消息列表，包含系统提示、用户输入、以及历史的工具调用与结果
     * @param tools 可用工具定义，传 null 表示本次不允许调用工具
     * @return 模型原始响应，正文可能为空而只带工具调用请求
     */
    public ChatResponse chat(List<ChatRequest.Message> messages, List<ToolDefinition> tools) {
        return send(messages, tools);
    }

    private ChatResponse send(List<ChatRequest.Message> messages, List<ToolDefinition> tools) {
        // 密钥缺失时直接报错，否则会带着空的 Authorization 头去调接口，只拿到一个 401
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("未配置大模型密钥，请设置环境变量 DASHSCOPE_API_KEY");
        }

        ChatRequest request = new ChatRequest(
                properties.model(),
                messages,
                tools,
                MAX_OUTPUT_TOKENS,
                TEMPERATURE,
                false,
                false);

        long start = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            // 把服务端返回的错误体带出来，否则只能看到一个 400，不知道是参数错了还是模型名不对
            throw new IllegalStateException("调用大模型失败，HTTP " + e.getStatusCode().value()
                    + "：" + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("调用大模型异常：" + e.getMessage(), e);
        }
        long elapsed = System.currentTimeMillis() - start;

        if (response == null) {
            throw new IllegalStateException("大模型返回为空");
        }

        log.info("大模型调用完成 model={} 耗时={}ms token={}+{} 可用工具数={}",
                properties.model(), elapsed, promptTokens(response), completionTokens(response),
                tools == null ? 0 : tools.size());

        return response;
    }

    private int promptTokens(ChatResponse response) {
        return response.usage() == null ? 0 : response.usage().promptTokens();
    }

    private int completionTokens(ChatResponse response) {
        return response.usage() == null ? 0 : response.usage().completionTokens();
    }

    private int totalTokens(ChatResponse response) {
        return response.usage() == null ? 0 : response.usage().totalTokens();
    }

    private SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }
}
