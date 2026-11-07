package com.example.oaticket.controller;

import com.example.oaticket.trace.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理
 *
 * 三类异常分开对待：参数错返回 400 让调用方改；服务端问题返回 500；
 * 未预期的异常兜底捕获，避免堆栈直接暴露给外部
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 入参不合法时返回 400
     *
     * @param e 参数校验异常
     * @return HTTP 400 与错误说明
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        log.warn("请求参数不合法: {}", e.getMessage());
        return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
    }

    /**
     * 模型调用失败或配置缺失时返回 500
     *
     * @param e 服务端异常
     * @return HTTP 500 与错误说明
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleServerError(IllegalStateException e) {
        log.error("服务端异常: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(errorBody(e.getMessage()));
    }

    /**
     * 兜底捕获未预期异常
     *
     * 不把原始异常信息返回给外部：堆栈里可能带表名、路径甚至连接串
     *
     * @param e 未预期异常
     * @return HTTP 500 与通用说明
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("未预期异常: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(errorBody("服务内部错误，请把 traceId 提供给管理员排查"));
    }

    /**
     * 错误响应统一带 traceId
     *
     * 用户报障时只需要给这一个号，管理员凭它在日志里搜出整条链路，
     * 不用问"你什么时候点的""请求参数是什么"
     */
    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("traceId", MDC.get(TraceIdFilter.TRACE_ID));
        return body;
    }
}
