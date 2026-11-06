package com.example.oaticket.tool;

import com.example.oaticket.model.Ticket;
import com.example.oaticket.model.TicketProposal;
import com.example.oaticket.model.ToolResult;
import com.example.oaticket.repository.TicketProposalRepository;
import com.example.oaticket.repository.TicketRepository;
import com.example.oaticket.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 工具：提交工单状态变更提案
 *
 * 这是唯一的写操作工具，但它并不真的改状态——只是往 ticket_proposals 写一条提案。
 * 真正落地要等人工调用确认接口。模型无法绕过这一步直接修改工单
 */
@Component
public class TicketActionTool {

    private static final Logger log = LoggerFactory.getLogger(TicketActionTool.class);
    private static final Set<String> ALLOWED_DECISIONS = Set.of("APPROVED", "REJECTED");

    private final TicketRepository ticketRepository;
    private final TicketProposalRepository proposalRepository;

    public TicketActionTool(TicketRepository ticketRepository, TicketProposalRepository proposalRepository) {
        this.ticketRepository = ticketRepository;
        this.proposalRepository = proposalRepository;
    }

    /**
     * 生成一条待人工确认的状态变更提案
     *
     * @param ticketNo 工单编号
     * @param decision 目标状态，只能是 APPROVED 或 REJECTED
     * @param reason 变更理由，会写进审计日志
     * @return 提案详情，其中 status 为 PENDING 表示尚未落地
     */
    public ToolResult<TicketProposal> proposeDecision(String ticketNo, String decision, String reason) {
        if (ticketNo == null || ticketNo.isBlank()) {
            return ToolResult.fail("工单编号不能为空");
        }
        if (decision == null || decision.isBlank()) {
            return ToolResult.fail("decision 不能为空，只能是 APPROVED 或 REJECTED");
        }
        if (reason == null || reason.isBlank()) {
            return ToolResult.fail("必须说明变更理由，这段理由会进入审计日志");
        }

        String normalized = decision.trim().toUpperCase();
        if (!ALLOWED_DECISIONS.contains(normalized)) {
            return ToolResult.fail("decision 只能是 APPROVED 或 REJECTED，收到的是 " + decision);
        }

        Optional<Ticket> found = ticketRepository.findByTicketNo(ticketNo.trim());
        if (found.isEmpty()) {
            return ToolResult.fail("未找到编号为 " + ticketNo.trim() + " 的工单");
        }

        Ticket ticket = found.get();
        // 只有待处理状态能变更，已批过的单子再提提案没有意义，还会掩盖重复审批的问题
        if (!"PENDING".equals(ticket.status())) {
            return ToolResult.fail("工单当前状态是 " + ticket.status() + "，只有待处理状态的工单可以变更");
        }

        String proposalId = "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TicketProposal proposal = new TicketProposal(
                null, proposalId, ticket.ticketNo(), ticket.status(), normalized, reason.trim(),
                TicketProposal.STATUS_PENDING, DateTimeUtils.now(), null, null);

        proposalRepository.save(proposal);

        log.info("生成待确认提案 proposalId={} ticket={} {} -> {}",
                proposalId, ticket.ticketNo(), ticket.status(), normalized);

        return ToolResult.ok(proposal);
    }
}
