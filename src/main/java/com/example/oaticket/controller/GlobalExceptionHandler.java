package com.example.oaticket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理
 *
 * 统一把异常转成带说明的 JSON，避免调用方只拿到一个没有内容的 500
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
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /**
     * 模型调用失败或配置缺失时返回 500
     *
     * @param e 服务端异常
     * @return HTTP 500 与错误说明
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleServerError(IllegalStateException e) {
        log.error("服务端异常: {}", e.getMessage());
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
}
