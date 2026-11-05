package com.example.oaticket.controller;

import com.example.oaticket.model.TicketProposal;
import com.example.oaticket.service.TicketApprovalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单状态变更的人工确认入口
 *
 * 模型只能提交提案，真正的状态变更必须经过这里的接口，且必须带上操作人
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final TicketApprovalService approvalService;

    public ApprovalController(TicketApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * GET /api/approvals/pending
     * 列出所有待确认的提案
     *
     * @return 待确认提案列表，没有时返回空数组
     */
    @GetMapping("/pending")
    public List<TicketProposal> pending() {
        return approvalService.pending();
    }

    /**
     * POST /api/approvals/{proposalId}/confirm
     * 确认提案，工单状态真正变更
     *
     * @param proposalId 提案编号，形如 P-1A2B3C4D
     * @param request 请求体，必须包含操作人
     * @return 处理后的提案
     */
    @PostMapping("/{proposalId}/confirm")
    public TicketProposal confirm(@PathVariable String proposalId, @RequestBody DecisionRequest request) {
        return approvalService.confirm(proposalId, request.operator());
    }

    /**
     * POST /api/approvals/{proposalId}/reject
     * 驳回提案，工单状态保持不变
     *
     * @param proposalId 提案编号
     * @param request 请求体，必须包含操作人
     * @return 处理后的提案
     */
    @PostMapping("/{proposalId}/reject")
    public TicketProposal reject(@PathVariable String proposalId, @RequestBody DecisionRequest request) {
        return approvalService.reject(proposalId, request.operator());
    }

    /**
     * 确认或驳回请求体
     */
    public record DecisionRequest(String operator) {
    }
}
