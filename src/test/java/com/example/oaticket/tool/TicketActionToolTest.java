package com.example.oaticket.tool;

import com.example.oaticket.model.TicketProposal;
import com.example.oaticket.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 写操作工具的测试
 *
 * 核心要验证的是"它真的没有改工单状态"——这是整个确认机制成立的前提
 */
@SpringBootTest
@Transactional
@DisplayName("工单状态变更提案工具")
class TicketActionToolTest {

    @Autowired
    private TicketActionTool ticketActionTool;

    @Autowired
    private com.example.oaticket.repository.TicketRepository ticketRepository;

    @Test
    @DisplayName("待处理工单能生成提案，且提案状态为 PENDING")
    void proposeDecision_pendingTicket_shouldCreateProposal() {
        ToolResult<TicketProposal> result = ticketActionTool.proposeDecision(
                "T-20261001-001", "APPROVED", "金额未超过差旅费上限");

        assertTrue(result.success());
        TicketProposal proposal = result.data();
        assertTrue(proposal.proposalId().startsWith("P-"));
        assertEquals("PENDING", proposal.fromStatus());
        assertEquals("APPROVED", proposal.toStatus());
        assertEquals(TicketProposal.STATUS_PENDING, proposal.status());
        assertNull(proposal.operator(), "还没人确认，操作人应为空");
    }

    @Test
    @DisplayName("生成提案不会修改工单状态")
    void proposeDecision_shouldNotChangeTicketStatus() {
        String before = ticketRepository.findByTicketNo("T-20261001-001").orElseThrow().status();

        ticketActionTool.proposeDecision("T-20261001-001", "APPROVED", "测试用");

        String after = ticketRepository.findByTicketNo("T-20261001-001").orElseThrow().status();
        assertEquals(before, after, "提案只是建议，工单状态一个字都不该动");
    }

    @Test
    @DisplayName("已处理的工单不能再提变更提案")
    void proposeDecision_processedTicket_shouldFail() {
        // T-20261009-008 在初始数据里是 APPROVED
        ToolResult<TicketProposal> result = ticketActionTool.proposeDecision(
                "T-20261009-008", "REJECTED", "重复提交");

        assertFalse(result.success());
        assertTrue(result.message().contains("只有待处理状态"));
    }

    @Test
    @DisplayName("不存在的工单返回失败")
    void proposeDecision_unknownTicket_shouldFail() {
        ToolResult<TicketProposal> result = ticketActionTool.proposeDecision(
                "T-99999999-999", "APPROVED", "测试");

        assertFalse(result.success());
        assertTrue(result.message().contains("未找到"));
    }

    @Test
    @DisplayName("decision 只能是 APPROVED 或 REJECTED")
    void proposeDecision_invalidDecision_shouldFail() {
        ToolResult<TicketProposal> result = ticketActionTool.proposeDecision(
                "T-20261001-001", "MAYBE", "测试");

        assertFalse(result.success());
        assertTrue(result.message().contains("APPROVED 或 REJECTED"));
    }

    @Test
    @DisplayName("缺少理由不能提交，审计日志需要这段说明")
    void proposeDecision_blankReason_shouldFail() {
        assertFalse(ticketActionTool.proposeDecision("T-20261001-001", "APPROVED", "  ").success());
        assertFalse(ticketActionTool.proposeDecision("T-20261001-001", "APPROVED", null).success());
    }

    @Test
    @DisplayName("参数为空时在入口拦下")
    void proposeDecision_blankArguments_shouldFail() {
        assertFalse(ticketActionTool.proposeDecision("", "APPROVED", "理由").success());
        assertFalse(ticketActionTool.proposeDecision(null, "APPROVED", "理由").success());
        assertFalse(ticketActionTool.proposeDecision("T-20261001-001", "", "理由").success());
    }
}
