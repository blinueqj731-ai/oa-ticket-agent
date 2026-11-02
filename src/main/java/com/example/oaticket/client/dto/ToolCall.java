package com.example.oaticket.client.dto;

/**
 * 模型请求的工具调用
 *
 * arguments 是 JSON 字符串而不是对象，需要再解析一次才能拿到参数
 */
public record ToolCall(String id, String type, Function function) {

    /**
     * 调用详情
     */
    public record Function(String name, String arguments) {
    }
}
