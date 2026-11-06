package com.example.oaticket.rag;

import com.example.oaticket.config.EmbeddingProperties;
import com.example.oaticket.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本向量化客户端
 *
 * 和 LlmClient 走同一个 base-url 和 apiKey，但单独成一个类：
 * 对话返回文本、向量化返回数字数组，是两类调用，错误处理混在一起会互相干扰
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    /** 百炼 embedding 单次最多 10 条，超了直接报错，所以必须分批 */
    private static final int MAX_BATCH = 10;

    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;
    private final RestClient restClient;

    public EmbeddingClient(LlmProperties llmProperties, EmbeddingProperties embeddingProperties) {
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
        this.restClient = RestClient.builder()
                .baseUrl(llmProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llmProperties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory())
                .build();
    }

    /**
     * 单条文本转向量
     *
     * @param text 待向量化的文本
     * @return 1024 维向量
     */
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量转向量，内部按 10 条自动分批
     *
     * @param texts 待向量化的文本列表
     * @return 与入参顺序一一对应的向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += MAX_BATCH) {
            out.addAll(embedChunk(texts.subList(i, Math.min(i + MAX_BATCH, texts.size()))));
        }
        return out;
    }

    private List<float[]> embedChunk(List<String> texts) {
        // 和 LlmClient 一致：密钥缺失时直接报错，不要带着空 Authorization 去换一个 401
        if (llmProperties.apiKey() == null || llmProperties.apiKey().isBlank()) {
            throw new IllegalStateException("未配置大模型密钥，请设置环境变量 DASHSCOPE_API_KEY");
        }

        Map<String, Object> body = Map.of(
                "model", embeddingProperties.model(),
                "input", texts);

        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientResponseException e) {
            // 把响应体带出来，否则只能看到一个 400，分不清是模型名错还是 Key 错
            throw new IllegalStateException("向量化失败，HTTP " + e.getStatusCode().value()
                    + "：" + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("向量化异常：" + e.getMessage(), e);
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("向量化返回为空");
        }

        List<float[]> out = new ArrayList<>(response.data().size());
        for (EmbeddingResponse.Item item : response.data()) {
            List<Double> raw = item.embedding();
            float[] vector = new float[raw.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = raw.get(i).floatValue();
            }
            out.add(vector);
        }
        log.debug("向量化完成 条数={} 维度={}", out.size(), out.get(0).length);
        return out;
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        // 向量化比对话快得多，但索引构建时是 10 条一批，给 20 秒足够
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    /**
     * /embeddings 的响应体，只取用得上的字段
     */
    public record EmbeddingResponse(List<Item> data) {

        public record Item(List<Double> embedding) {
        }
    }
}
