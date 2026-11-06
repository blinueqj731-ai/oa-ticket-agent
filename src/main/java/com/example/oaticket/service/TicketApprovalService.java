package com.example.oaticket.service;

import com.example.oaticket.model.TicketProposal;
import com.example.oaticket.repository.OperationLogRepository;
import com.example.oaticket.repository.TicketProposalRepository;
import com.example.oaticket.repository.TicketRepository;
import com.example.oaticket.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工单状态变更的确认与驳回
 *
 * 这里的每个方法都要同时改三张表：工单、提案、审计日志。
 * 三步必须在一个事务里，任何一步失败都整体回滚，不能出现"工单改完了但日志没记"的情况
 */
@Service
public class TicketApprovalService {

    private static final Logger log = LoggerFactory.getLogger(TicketApprovalService.class);

    private final TicketRepository ticketRepository;
    private final TicketProposalRepository proposalRepository;
    private final OperationLogRepository operationLogRepository;

    public TicketApprovalService(TicketRepository ticketRepository,
                                 TicketProposalRepository proposalRepository,
                                 OperationLogRepository operationLogRepository) {
        this.ticketRepository = ticketRepository;
        this.proposalRepository = proposalRepository;
        this.operationLogRepository = operationLogRepository;
    }

    /**
     * 确认提案，真正把工单状态改掉
     *
     * @param proposalId 提案编号
     * @param operator 操作人，写入审计日志
     * @return 处理后的提案
     */
    @Transactional
    public TicketProposal confirm(String proposalId, String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("操作人不能为空，审计日志需要记录是谁确认的");
        }

        TicketProposal proposal = requirePending(proposalId);

        // 乐观锁：只有工单当前状态仍等于提案生成时的状态才更新
        int updated = ticketRepository.updateStatus(
                proposal.ticketNo(), proposal.fromStatus(), proposal.toStatus());
        if (updated == 0) {
            throw new IllegalStateException("工单状态已变化，提案失效，请重新生成提案");
        }

        String now = DateTimeUtils.now();
        int decided = proposalRepository.updateDecision(
                proposalId, TicketProposal.STATUS_CONFIRMED, operator.trim(), now);
        if (decided == 0) {
            // 事务会回滚，上面那次工单更新也会被撤销
            throw new IllegalStateException("提案已被其他人处理，请刷新后重试");
        }

        operationLogRepository.insert(
                proposal.ticketNo(),
                "CONFIRM",
                operator.trim(),
                "提案 " + proposalId + "：" + proposal.fromStatus() + " -> " + proposal.toStatus()
                        + "；理由：" + proposal.reason(),
                now);

        log.info("提案已确认 proposalId={} ticket={} operator={}", proposalId, proposal.ticketNo(), operator);
        return proposalRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new IllegalStateException("提案在确认后消失，数据库状态异常"));
    }

    /**
     * 驳回提案，工单状态保持不变
     *
     * @param proposalId 提案编号
     * @param operator 操作人
     * @return 处理后的提案
     */
    @Transactional
    public TicketProposal reject(String proposalId, String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("操作人不能为空，审计日志需要记录是谁驳回的");
        }

        TicketProposal proposal = requirePending(proposalId);
        String now = DateTimeUtils.now();

        int decided = proposalRepository.updateDecision(
                proposalId, TicketProposal.STATUS_REJECTED, operator.trim(), now);
        if (decided == 0) {
            throw new IllegalStateException("提案已被其他人处理，请刷新后重试");
        }

        operationLogRepository.insert(
                proposal.ticketNo(),
                "REJECT",
                operator.trim(),
                "提案 " + proposalId + " 被驳回，工单状态保持不变",
                now);

        log.info("提案已驳回 proposalId={} ticket={} operator={}", proposalId, proposal.ticketNo(), operator);
        return proposalRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new IllegalStateException("提案在驳回后消失，数据库状态异常"));
    }

    /**
     * 列出所有待确认的提案
     *
     * @return 待确认提案列表，没有时返回空列表
     */
    public List<TicketProposal> pending() {
        return proposalRepository.findByStatus(TicketProposal.STATUS_PENDING);
    }

    private TicketProposal requirePending(String proposalId) {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("提案编号不能为空");
        }
        TicketProposal proposal = proposalRepository.findByProposalId(proposalId.trim())
                .orElseThrow(() -> new IllegalArgumentException("提案不存在：" + proposalId));
        if (!TicketProposal.STATUS_PENDING.equals(proposal.status())) {
            throw new IllegalArgumentException("提案已被处理，当前状态：" + proposal.status());
        }
        return proposal;
    }
}
