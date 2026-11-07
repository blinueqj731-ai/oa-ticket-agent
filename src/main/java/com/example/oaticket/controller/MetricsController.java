package com.example.oaticket.controller;

import com.example.oaticket.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运行时指标查询
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * GET /api/metrics
     * 查看成功率、P95 延迟、token 消耗与工具失败率
     *
     * @return 指标快照，重启后清零
     */
    @GetMapping
    public Map<String, Object> metrics() {
        return metricsService.snapshot();
    }
}
