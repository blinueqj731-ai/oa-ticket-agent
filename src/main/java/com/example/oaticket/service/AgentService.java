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
 * 循环执行"调模型、执行工具、把结果回传"这套流程，直到模型不再请求工具为止
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    // 步数上限先给一个宽松的值，防止模型反复调工具停不下来
    private static final int MAX_STEPS = 10;

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
            - 信息齐了就停止调用工具，直接给结论，不要重复查同一个数据
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

        List<AgentResult.ToolInvocation> invocations = new ArrayList<>();
        int totalTokens = 0;
        int step = 0;

        while (step < MAX_STEPS) {
            step++;
            ChatResponse response = llmClient.chat(messages, toolRegistry.definitions());
            totalTokens += totalTokens(response);

            List<ToolCall> toolCalls = response.toolCalls();
            if (toolCalls.isEmpty()) {
                log.info("第 {} 步：模型给出结论，处理结束 工具调用={} 次 总token={}",
                        step, invocations.size(), totalTokens);
                return new AgentResult(response.content(), invocations, totalTokens);
            }

            log.info("第 {} 步：模型请求调用 {} 个工具", step, toolCalls.size());

            // 必须把模型的调用请求原样放回上下文，否则下一轮的 tool 消息找不到对应的 tool_call_id
            messages.add(ChatRequest.Message.assistant(response.content(), toolCalls));

            for (ToolCall call : toolCalls) {
                String result = toolRegistry.execute(call);
                messages.add(ChatRequest.Message.tool(call.id(), result));
                invocations.add(new AgentResult.ToolInvocation(
                        call.function().name(), call.function().arguments(), result));
            }
        }

        log.warn("达到步数上限 {}，强制结束 工具调用={} 次 总token={}",
                MAX_STEPS, invocations.size(), totalTokens);

        return new AgentResult(
                "处理过程超过 " + MAX_STEPS + " 步仍未得出结论，已中止。已调用的工具记录见 toolInvocations",
                invocations, totalTokens);
    }

    private int totalTokens(ChatResponse response) {
        return response.usage() == null ? 0 : response.usage().totalTokens();
    }
}
