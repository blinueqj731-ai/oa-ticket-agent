package com.example.oaticket.controller;

import com.example.oaticket.model.AgentResult;
import com.example.oaticket.service.AgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单处理入口
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * POST /api/agent/ticket
     * 提交一个问题，由 Agent 查数据后给出处理建议
     *
     * @param request 请求体，只有一个 question 字段
     * @return 建议正文、工具调用记录与 token 消耗
     */
    @PostMapping("/ticket")
    public AgentResult handleTicket(@RequestBody AgentRequest request) {
        return agentService.handle(request.question());
    }

    /**
     * 工单处理请求体
     */
    public record AgentRequest(String question) {
    }
}
