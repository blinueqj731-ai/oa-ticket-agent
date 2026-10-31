package com.example.oaticket.repository;

import com.example.oaticket.model.ApprovalRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 审批规则数据访问
 */

@Repository
public class ApprovalRuleRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, ticket_type, max_amount, required_level, description FROM approval_rules";

    private final JdbcTemplate jdbcTemplate;

    public ApprovalRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按工单类型查规则
     * 返回 List 而不是 Optional：同一个类型可能对应多条规则（比如报销有差旅费、办公用品、招待费三条），
     * 具体适用哪条由模型结合工单内容判断，这里只负责把候选规则全部给出。
     */
    public List<ApprovalRule> findByTicketType(String ticketType) {
        String sql = SELECT_COLUMNS + " WHERE ticket_type = ? ORDER BY id";
        return jdbcTemplate.query(sql, this::mapRow, ticketType);
    }

    public List<ApprovalRule> findAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY id", this::mapRow);
    }

    private ApprovalRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        // max_amount 可能是 NULL（表示不限额），必须用 wasNull 判断，否则会变成 0
        double amountValue = rs.getDouble("max_amount");
        Double maxAmount = rs.wasNull() ? null : amountValue;

        return new ApprovalRule(
                rs.getLong("id"),
                rs.getString("ticket_type"),
                maxAmount,
                rs.getString("required_level"),
                rs.getString("description"));
    }
}
