package com.example.oaticket.model;

/**
 * 工具的统一返回格式
 */

public record ToolResult<T>(boolean success, T data, String message) {

    public static <T> ToolResult<T> ok(T data) {
        return new ToolResult<>(true, data, "查询成功");
    }

    public static <T> ToolResult<T> fail(String message) {
        return new ToolResult<>(false, null, message);
    }
}
