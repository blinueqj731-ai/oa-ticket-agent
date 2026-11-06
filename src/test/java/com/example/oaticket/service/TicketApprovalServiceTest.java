package com.example.oaticket.service;

import com.example.oaticket.model.TicketProposal;
import com.example.oaticket.repository.OperationLogRepository;
import com.example.oaticket.repository.TicketProposalRepository;
import com.example.oaticket.repository.TicketRepository;
import com.example.oaticket.tool.TicketActionTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确认与驳回的测试
 *
 * 重点是两个乐观锁分支：工单被别人改过、提案被别人处理过。
 * 这两条路径如果写错，会出现"后一次确认悄悄覆盖前一次"的严重问题
 */
@SpringBootTest
@Transactional
@DisplayName("工单状态确认与驳回")
class TicketApprovalServiceTest {

    @Autowired
    private TicketApprovalService approvalService;

    @Autowired
    private TicketActionTool actionTool;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketProposalRepository proposalRepository;

    @Autowired
    private OperationLogRepository logRepository;

    @Test
    @DisplayName("确认后工单状态变更，提案置为 CONFIRMED，审计日志留痕")
    void confirm_shouldUpdateTicketAndWriteLog() {
        String ticketNo = "T-20261002-003";
        TicketProposal proposal = actionTool
                .proposeDecision(ticketNo, "APPROVED", "办公用品未超 2000 元").data();

        TicketProposal confirmed = approvalService.confirm(proposal.proposalId(), "张三");

        assertEquals(TicketProposal.STATUS_CONFIRMED, confirmed.status());
        assertEquals("张三", confirmed.operator());
        assertEquals("APPROVED", ticketRepository.findByTicketNo(ticketNo).orElseThrow().status());
        assertFalse(logRepository.findByTicketNo(ticketNo).isEmpty(), "确认操作必须留审计日志");
    }

    @Test
    @DisplayName("驳回后工单状态保持不变")
    void reject_shouldKeepTicketStatus() {
        String ticketNo = "T-20261002-003";
        TicketProposal proposal = actionTool
                .proposeDecision(ticketNo, "APPROVED", "测试驳回").data();

        TicketProposal rejected = approvalService.reject(proposal.proposalId(), "李四");

        assertEquals(TicketProposal.STATUS_REJECTED, rejected.status());
        assertEquals("PENDING", ticketRepository.findByTicketNo(ticketNo).orElseThrow().status(),
                "驳回了就不该动工单");
    }

    @Test
    @DisplayName("同一条提案不能被确认两次")
    void confirm_twice_shouldFailOnSecondTime() {
        TicketProposal proposal = actionTool
                .proposeDecision("T-20261002-003", "APPROVED", "测试重复确认").data();

        approvalService.confirm(proposal.proposalId(), "张三");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> approvalService.confirm(proposal.proposalId(), "李四"));
        assertTrue(ex.getMessage().contains("已被处理"));
    }

    @Test
    @DisplayName("工单状态被外部改过时，旧提案失效")
    void confirm_afterTicketChanged_shouldFail() {
        String ticketNo = "T-20261002-003";
        TicketProposal proposal = actionTool
                .proposeDecision(ticketNo, "APPROVED", "提案是基于 PENDING 生成的").data();

        // 模拟另一个人绕过提案直接改了工单
        ticketRepository.updateStatus(ticketNo, "PENDING", "REJECTED");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> approvalService.confirm(proposal.proposalId(), "张三"));
        assertTrue(ex.getMessage().contains("提案失效"));
    }

    @Test
    @DisplayName("确认不存在的提案直接报错")
    void confirm_unknownProposal_shouldFail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> approvalService.confirm("P-NOTEXIST", "张三"));
        assertTrue(ex.getMessage().contains("提案不存在"));
    }

    @Test
    @DisplayName("操作人为空时报错，审计日志不能没有责任人")
    void confirm_blankOperator_shouldFail() {
        TicketProposal proposal = actionTool
                .proposeDecision("T-20261002-003", "APPROVED", "测试").data();

        assertThrows(IllegalArgumentException.class,
                () -> approvalService.confirm(proposal.proposalId(), "  "));
        assertThrows(IllegalArgumentException.class,
                () -> approvalService.confirm(proposal.proposalId(), null));
    }

    @Test
    @DisplayName("待确认列表能查到刚生成的提案")
    void pending_shouldContainNewProposal() {
        TicketProposal proposal = actionTool
                .proposeDecision("T-20261002-003", "APPROVED", "测试列表").data();

        assertTrue(approvalService.pending().stream()
                .anyMatch(p -> p.proposalId().equals(proposal.proposalId())));
    }

    @Test
    @DisplayName("确认前后工单状态确实发生过变化")
    void confirm_statusShouldActuallyChange() {
        String ticketNo = "T-20261002-003";
        String before = ticketRepository.findByTicketNo(ticketNo).orElseThrow().status();
        TicketProposal proposal = actionTool
                .proposeDecision(ticketNo, "APPROVED", "测试状态变化").data();

        approvalService.confirm(proposal.proposalId(), "张三");

        String after = ticketRepository.findByTicketNo(ticketNo).orElseThrow().status();
        assertNotEquals(before, after);
        assertEquals("APPROVED", after);
    }
}
