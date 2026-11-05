package com.example.oaticket.repository;

import com.example.oaticket.model.TicketProposal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 待确认提案数据访问
 */
@Repository
public class TicketProposalRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, proposal_id, ticket_no, from_status, to_status, reason, status, "
                    + "created_at, decided_at, operator FROM ticket_proposals";

    private final JdbcTemplate jdbcTemplate;

    public TicketProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(TicketProposal proposal) {
        jdbcTemplate.update(
                "INSERT INTO ticket_proposals "
                        + "(proposal_id, ticket_no, from_status, to_status, reason, status, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)",
                proposal.proposalId(), proposal.ticketNo(), proposal.fromStatus(),
                proposal.toStatus(), proposal.reason(), proposal.status(), proposal.createdAt());
    }

    public Optional<TicketProposal> findByProposalId(String proposalId) {
        List<TicketProposal> rows = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE proposal_id = ?", this::mapRow, proposalId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<TicketProposal> findByStatus(String status) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = ? ORDER BY id DESC", this::mapRow, status);
    }

    /**
     * 记录人工的处理结果
     *
     * 带 status 条件：只有仍处于 PENDING 的提案才能被处理，
     * 防止重复点击确认把同一条提案处理两次
     *
     * @return 实际影响行数，0 表示提案已被别人处理过
     */
    public int updateDecision(String proposalId, String status, String operator, String decidedAt) {
        return jdbcTemplate.update(
                "UPDATE ticket_proposals SET status = ?, operator = ?, decided_at = ? "
                        + "WHERE proposal_id = ? AND status = ?",
                status, operator, decidedAt, proposalId, TicketProposal.STATUS_PENDING);
    }

    private TicketProposal mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TicketProposal(
                rs.getLong("id"),
                rs.getString("proposal_id"),
                rs.getString("ticket_no"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("decided_at"),
                rs.getString("operator"));
    }
}
