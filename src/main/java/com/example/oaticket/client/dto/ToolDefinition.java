package com.example.oaticket.client.dto;

import java.util.Map;

/**
 * 提供给模型的工具定义，格式对应 OpenAI 的 function calling
 */
public record ToolDefinition(String type, Function function) {

    /**
     * 工具函数描述
     */
    public record Function(String name, String description, Map<String, Object> parameters) {
    }

    /**
     * 构造一个工具定义
     *
     * @param name 工具名，模型调用时用它定位
     * @param description 工具用途，模型靠这句判断什么时候该调
     * @param parameters JSON Schema，描述入参结构
     * @return 类型为 function 的工具定义
     */
    public static ToolDefinition function(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition("function", new Function(name, description, parameters));
    }
}
