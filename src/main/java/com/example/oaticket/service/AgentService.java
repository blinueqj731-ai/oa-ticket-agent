package com.example.oaticket.service;

import com.example.oaticket.client.LlmClient;
import com.example.oaticket.client.dto.ChatRequest;
import com.example.oaticket.client.dto.ChatResponse;
import com.example.oaticket.client.dto.ToolCall;
import com.example.oaticket.config.AgentProperties;
import com.example.oaticket.model.AgentResult;
import com.example.oaticket.model.StopReason;
import com.example.oaticket.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工单处理 Agent
 *
 * 循环执行"调模型、执行工具、把结果回传"，直到模型不再请求工具为止。
 * 步数、墙钟、token 三个上限任一触发都会停止循环并降级，不抛异常
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
            - 信息齐了就停止调用工具，直接给结论，不要重复查同一个数据
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AgentProperties agentProperties;

    public AgentService(LlmClient llmClient, ToolRegistry toolRegistry, AgentProperties agentProperties) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.agentProperties = agentProperties;
    }

    /**
     * 处理一次工单咨询
     *
     * @param question 用户的问题
     * @return 最终回复、工具调用记录、总 token、结束原因与执行步数
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
        long deadline = System.currentTimeMillis() + agentProperties.timeoutSeconds() * 1000L;

        while (step < agentProperties.maxSteps()) {
            // 墙钟按"整次请求"计算而不是单步，避免步数少但每步都慢的情况漏网
            if (System.currentTimeMillis() > deadline) {
                return degrade(StopReason.TIMEOUT, step, invocations, totalTokens);
            }
            // 预算检查放在发送请求之前，避免明知超预算还发一次
            if (totalTokens > agentProperties.tokenBudget()) {
                return degrade(StopReason.TOKEN_BUDGET, step, invocations, totalTokens);
            }

            step++;
            long stepStart = System.currentTimeMillis();
            ChatResponse response = llmClient.chat(messages, toolRegistry.definitions());
            long stepElapsed = System.currentTimeMillis() - stepStart;
            int stepTokens = totalTokens(response);
            totalTokens += stepTokens;

            List<ToolCall> toolCalls = response.toolCalls();
            log.info("第 {} 步 | 耗时={}ms | 本步token={} | 累计token={} | 请求工具={} 个",
                    step, stepElapsed, stepTokens, totalTokens, toolCalls.size());

            if (toolCalls.isEmpty()) {
                log.info("处理完成 | 共 {} 步 | 工具调用={} 次 | 总token={}", step, invocations.size(), totalTokens);
                return new AgentResult(response.content(), invocations, totalTokens, StopReason.COMPLETED, step);
            }

            // 必须把模型的调用请求原样放回上下文，否则下一轮的 tool 消息找不到对应的 tool_call_id
            messages.add(ChatRequest.Message.assistant(response.content(), toolCalls));

            for (ToolCall call : toolCalls) {
                String result = toolRegistry.execute(call);
                messages.add(ChatRequest.Message.tool(call.id(), result));
                invocations.add(new AgentResult.ToolInvocation(
                        call.function().name(), call.function().arguments(), result));
            }
        }

        return degrade(StopReason.MAX_STEPS, step, invocations, totalTokens);
    }

    /**
     * 构造降级结果
     *
     * 降级不抛异常，把已收集到的信息一并返回，交给人工接手
     *
     * @param reason 触发的中止原因
     * @param steps 已执行的步数
     * @param invocations 已完成的工具调用
     * @param totalTokens 已消耗的 token
     * @return 带中止标记的结果
     */
    private AgentResult degrade(StopReason reason, int steps,
                                List<AgentResult.ToolInvocation> invocations, int totalTokens) {
        log.warn("Agent 被迫中止 | 原因={} | 已执行={} 步 | 工具调用={} 次 | 累计token={}",
                reason.displayName(), steps, invocations.size(), totalTokens);

        String content = "处理未完成：" + reason.displayName()
                + "。已执行 " + steps + " 步、调用工具 " + invocations.size() + " 次，"
                + "已收集到的数据见 toolInvocations，建议转人工继续处理。";

        return new AgentResult(content, invocations, totalTokens, reason, steps);
    }

    private int totalTokens(ChatResponse response) {
        return response.usage() == null ? 0 : response.usage().totalTokens();
    }
}
