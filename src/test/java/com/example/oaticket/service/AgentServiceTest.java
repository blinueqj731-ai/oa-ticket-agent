package com.example.oaticket.service;

import com.example.oaticket.client.LlmClient;
import com.example.oaticket.client.dto.ChatResponse;
import com.example.oaticket.client.dto.ToolCall;
import com.example.oaticket.client.dto.ToolDefinition;
import com.example.oaticket.config.AgentProperties;
import com.example.oaticket.model.AgentResult;
import com.example.oaticket.model.StopReason;
import com.example.oaticket.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 循环的测试
 *
 * 用 mock 替掉模型客户端，这样能精确控制"模型返回什么"，
 * 才能稳定复现步数超限和预算耗尽这两条降级路径——真实模型不可能配合演出
 */
@DisplayName("Agent 循环与降级")
class AgentServiceTest {

    private LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private AgentService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        toolRegistry = mock(ToolRegistry.class);
        when(toolRegistry.definitions()).thenReturn(List.<ToolDefinition>of());
        when(toolRegistry.execute(any())).thenReturn("{\"success\":true}");
        service = new AgentService(llmClient, toolRegistry, new AgentProperties(3, 60, 8000));
    }

    @Test
    @DisplayName("模型不请求工具时一步结束")
    void handle_noToolCall_completedInOneStep() {
        when(llmClient.chat(anyList(), anyList())).thenReturn(reply("直接回答", null, 100));

        AgentResult result = service.handle("问题");

        assertEquals(StopReason.COMPLETED, result.stopReason());
        assertEquals(1, result.steps());
        assertEquals("直接回答", result.content());
        assertTrue(result.toolInvocations().isEmpty());
        assertEquals(1, result.steps(), "没有工具调用时不该有第二步");
    }

    @Test
    @DisplayName("模型请求工具时先执行，拿到结论才结束")
    void handle_toolCallThenConclusion() {
        when(llmClient.chat(anyList(), anyList()))
                .thenReturn(reply(null, List.of(toolCall("get_ticket")), 100))
                .thenReturn(reply("查完了，结论是……", null, 100));

        AgentResult result = service.handle("问题");

        assertEquals(StopReason.COMPLETED, result.stopReason());
        assertEquals(2, result.steps());
        assertEquals(1, result.toolInvocations().size());
        assertEquals("get_ticket", result.toolInvocations().get(0).toolName());
        assertEquals(200, result.totalTokens(), "两轮 token 要累加");
    }

    @Test
    @DisplayName("模型一直要工具时，达到步数上限降级而不是无限跑")
    void handle_alwaysToolCall_degradeByMaxSteps() {
        when(llmClient.chat(anyList(), anyList()))
                .thenReturn(reply(null, List.of(toolCall("get_ticket")), 100));

        AgentResult result = service.handle("问题");

        assertEquals(StopReason.MAX_STEPS, result.stopReason());
        assertEquals(3, result.steps(), "上限是 3，跑满就该停");
        assertTrue(result.degraded());
        assertTrue(result.toolInvocations().size() >= 3);
    }

    @Test
    @DisplayName("token 超过预算时在下一轮开始前停下")
    void handle_overBudget_degradeByToken() {
        AgentService tight = new AgentService(llmClient, toolRegistry, new AgentProperties(10, 60, 100));
        when(llmClient.chat(anyList(), anyList()))
                .thenReturn(reply(null, List.of(toolCall("get_ticket")), 500));

        AgentResult result = tight.handle("问题");

        assertEquals(StopReason.TOKEN_BUDGET, result.stopReason());
        assertTrue(result.degraded());
        assertEquals(1, result.steps(), "第一轮就超预算，第二轮不该再发请求");
    }

    @Test
    @DisplayName("空问题在入口拒绝，不消耗模型调用")
    void handle_blankQuestion_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.handle("   "));
        assertThrows(IllegalArgumentException.class, () -> service.handle(null));
    }

    private ChatResponse reply(String content, List<ToolCall> toolCalls, int totalTokens) {
        return new ChatResponse(
                List.of(new ChatResponse.Choice(
                        new ChatResponse.Message("assistant", content, null, toolCalls), "stop")),
                new ChatResponse.Usage(totalTokens / 2, totalTokens / 2, totalTokens));
    }

    private ToolCall toolCall(String name) {
        return new ToolCall("call_1", "function",
                new ToolCall.Function(name, "{\"ticketNo\":\"T-20261001-001\"}"));
    }
}
