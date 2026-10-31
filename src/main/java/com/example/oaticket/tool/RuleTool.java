package com.example.oaticket.tool;

import com.example.oaticket.model.ApprovalRule;
import com.example.oaticket.model.ToolResult;
import com.example.oaticket.repository.ApprovalRuleRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具：按工单类型查询审批规则。
 *
 * 注意它返回的是"候选规则列表"而不是"唯一答案"。
 * 比如报销类返回三条规则（差旅费 / 办公用品 / 招待费），
 * 具体适用哪一条要看工单标题和内容，这一步交给模型判断。
 *
 * 代码不做关键词硬匹配，是因为标题写法千变万化，
 * 写死在代码里的匹配规则一定覆盖不全，还不如让模型看一眼内容。
 */

@Component
public class RuleTool {

    private final ApprovalRuleRepository approvalRuleRepository;

    public RuleTool(ApprovalRuleRepository approvalRuleRepository) {
        this.approvalRuleRepository = approvalRuleRepository;
    }

    /**
     * @param ticketType 工单类型，取值应为：报销 / 请假 / 权限申请 / 设备报修
     */
    public ToolResult<List<ApprovalRule>> getRules(String ticketType) {
        if (ticketType == null || ticketType.isBlank()) {
            return ToolResult.fail("工单类型不能为空，可选值：报销 / 请假 / 权限申请 / 设备报修");
        }

        List<ApprovalRule> rules = approvalRuleRepository.findByTicketType(ticketType.trim());
        if (rules.isEmpty()) {
            return ToolResult.fail("没有找到「" + ticketType.trim()
                    + "」类型的审批规则，可选值：报销 / 请假 / 权限申请 / 设备报修");
        }

        return ToolResult.ok(rules);
    }
}
