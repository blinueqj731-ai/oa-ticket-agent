package com.example.oaticket.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求分配 trace_id
 *
 * 放在过滤器里而不是在各个 Controller 里生成，是为了让日志、工具调用、模型请求
 * 共享同一个标识。排查"这次回答为什么不对"时，拿 trace_id 一搜就能捞到全部链路
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 里的键名，日志格式用 %X{traceId} 取 */
    public static final String TRACE_ID = "traceId";

    /** 响应头名字，调用方拿得到这个值，反馈问题时直接报号 */
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 上游已经传了就复用，便于跨服务串联；没传才新生成
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清：Tomcat 线程是复用的，不清会把上一个请求的 trace_id 带给下一个
            MDC.remove(TRACE_ID);
        }
    }
}
