package com.example.oaticket.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行时指标
 *
 * 全部放在内存里，重启即清零。这个项目是单实例演示，够用；
 * 多实例部署要换成 Micrometer + Prometheus，由指标服务端聚合
 */
@Component
public class MetricsService {

    /** 只保留最近这么多次延迟样本，用于算 P95。样本太多会吃内存，太少 P95 不准 */
    private static final int LATENCY_WINDOW = 200;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successRequests = new AtomicLong();
    private final AtomicLong degradedRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final AtomicLong toolCalls = new AtomicLong();
    private final AtomicLong toolFailures = new AtomicLong();
    private final Deque<Long> recentLatencies = new ConcurrentLinkedDeque<>();

    /**
     * 记录一次正常完成
     *
     * @param elapsedMs 端到端耗时
     * @param tokens 本次消耗的 token
     */
    public void recordSuccess(long elapsedMs, int tokens) {
        totalRequests.incrementAndGet();
        successRequests.incrementAndGet();
        totalTokens.addAndGet(tokens);
        recordLatency(elapsedMs);
    }

    /**
     * 记录一次降级结束（步数、超时或预算触发）
     *
     * @param elapsedMs 端到端耗时
     * @param tokens 已消耗的 token
     */
    public void recordDegraded(long elapsedMs, int tokens) {
        totalRequests.incrementAndGet();
        degradedRequests.incrementAndGet();
        totalTokens.addAndGet(tokens);
        recordLatency(elapsedMs);
    }

    /**
     * 记录一次异常失败
     *
     * @param elapsedMs 端到端耗时
     */
    public void recordFailure(long elapsedMs) {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
        recordLatency(elapsedMs);
    }

    /**
     * 记录一次工具调用
     *
     * @param success 工具是否正常返回
     */
    public void recordToolCall(boolean success) {
        toolCalls.incrementAndGet();
        if (!success) {
            toolFailures.incrementAndGet();
        }
    }

    /**
     * 当前指标快照
     *
     * @return 成功率、P95 延迟、token 总量、工具失败率
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        long total = totalRequests.get();

        result.put("totalRequests", total);
        result.put("success", successRequests.get());
        result.put("degraded", degradedRequests.get());
        result.put("failed", failedRequests.get());
        result.put("successRate", percent(successRequests.get(), total));
        result.put("avgLatencyMs", avgLatency());
        result.put("p95LatencyMs", p95Latency());
        result.put("totalTokens", totalTokens.get());
        result.put("toolCalls", toolCalls.get());
        result.put("toolFailureRate", percent(toolFailures.get(), toolCalls.get()));
        return result;
    }

    private void recordLatency(long elapsedMs) {
        recentLatencies.addLast(elapsedMs);
        // 超出窗口就丢掉最老的样本，保证内存恒定
        while (recentLatencies.size() > LATENCY_WINDOW) {
            recentLatencies.pollFirst();
        }
    }

    private String percent(long part, long total) {
        if (total == 0) {
            return "n/a";
        }
        return String.format("%.1f%%", part * 100.0 / total);
    }

    private long avgLatency() {
        List<Long> samples = new ArrayList<>(recentLatencies);
        if (samples.isEmpty()) {
            return 0;
        }
        return samples.stream().mapToLong(Long::longValue).sum() / samples.size();
    }

    private long p95Latency() {
        List<Long> samples = new ArrayList<>(recentLatencies);
        if (samples.isEmpty()) {
            return 0;
        }
        Collections.sort(samples);
        int index = (int) Math.ceil(samples.size() * 0.95) - 1;
        return samples.get(Math.max(0, Math.min(index, samples.size() - 1)));
    }
}
