package com.example.oaticket.service;

import com.example.oaticket.client.LlmClient;
import com.example.oaticket.client.dto.ChatRequest;
import com.example.oaticket.client.dto.ChatResponse;
import com.example.oaticket.client.dto.ToolCall;
import com.example.oaticket.model.AgentResult;
import com.example.oaticket.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工单处理 Agent
 *
 * 当前只支持一轮工具调用：模型请求调工具、拿到结果后再回答一次就结束。
 * 模型如果在这一轮之后还想调工具，这里不会继续
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String SYSTEM_PROMPT = """
            你是 OA 工单处理助手。你可以调用工具查询系统里的真实数据，共有三个：
            - get_ticket：按工单编号查工单详情
            - get_employee：按工号查员工信息
            - get_rules：按工单类型查审批规则

            处理一张工单时按这个顺序获取信息：
            1. 先查工单详情，拿到类型、金额和申请人工号
            2. 根据申请人工号查员工，确认部门和直属上级
            3. 根据工单类型查审批规则
            4. 综合三份数据给出处理建议

            回答要求：
            - 只能依据工具返回的数据作答，查不到就说查不到，不要推测
            - 给出建议时说明依据了哪条规则、该找哪位审批人
            - 如果工具返回 success 为 false，如实告知用户并说明原因
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public AgentService(LlmClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 处理一次工单咨询
     *
     * @param question 用户的问题
     * @return 最终回复、中途的工具调用记录与总 token 消耗
     */
    public AgentResult handle(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题内容不能为空");
        }

        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(ChatRequest.Message.system(SYSTEM_PROMPT));
        messages.add(ChatRequest.Message.user(question));

        ChatResponse first = llmClient.chat(messages, toolRegistry.definitions());
        int totalTokens = totalTokens(first);

        List<ToolCall> toolCalls = first.toolCalls();
        if (toolCalls.isEmpty()) {
            log.info("模型未请求工具，直接作答");
            return new AgentResult(first.content(), List.of(), totalTokens);
        }

        log.info("模型请求调用 {} 个工具", toolCalls.size());

        // 必须把模型的调用请求原样放回上下文，否则下一轮的 tool 消息找不到对应的 tool_call_id
        messages.add(ChatRequest.Message.assistant(first.content(), toolCalls));

        List<AgentResult.ToolInvocation> invocations = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            String result = toolRegistry.execute(call);
            messages.add(ChatRequest.Message.tool(call.id(), result));
            invocations.add(new AgentResult.ToolInvocation(
                    call.function().name(), call.function().arguments(), result));
        }

        // 第二轮不再提供工具，避免模型继续要求调用
        ChatResponse second = llmClient.chat(messages, null);
        totalTokens += totalTokens(second);

        String content = second.content() == null ? "模型未返回正文" : second.content();
        log.info("处理完成 工具调用={} 次 总token={}", invocations.size(), totalTokens);

        return new AgentResult(content, invocations, totalTokens);
    }

    private int totalTokens(ChatResponse response) {
        return response.usage() == null ? 0 : response.usage().totalTokens();
    }
}
