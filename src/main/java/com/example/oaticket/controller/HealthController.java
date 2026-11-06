package com.example.oaticket.controller;

import com.example.oaticket.utils.DateTimeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口
 */

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        // LinkedHashMap 保证输出顺序和下面 put 的顺序一致
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "oa-ticket-agent");
        result.put("time", DateTimeUtils.now());
        return result;
    }
}
