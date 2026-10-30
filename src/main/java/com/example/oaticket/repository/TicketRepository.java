package com.example.oaticket.repository;

import com.example.oaticket.model.Ticket;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 工单数据访问
 */
@Repository
public class TicketRepository {

    /**
     * 查询列抽成常量，避免 findAll 和 findByTicketNo 两处写重复的字段列表，
     * 以后加字段只改一个地方。
     */
    private static final String SELECT_COLUMNS =
            "SELECT id, ticket_no, type, title, content, applicant_no, amount, status, created_at FROM tickets";

    private final JdbcTemplate jdbcTemplate;

    public TicketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查全部工单，按创建时间倒序，最新的排前面
     */
    public List<Ticket> findAll() {
        String sql = SELECT_COLUMNS + " ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, this::mapRow);
    }

    /**
     * 按工单编号查单条
     * 返回 Optional 而不是 null：调用方必须显式处理"查不到"的情况，
     * 减少 NullPointerException 的可能。
     */
    public Optional<Ticket> findByTicketNo(String ticketNo) {
        String sql = SELECT_COLUMNS + " WHERE ticket_no = ?";
        List<Ticket> rows = jdbcTemplate.query(sql, this::mapRow, ticketNo);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 按状态统计数量，用于健康检查或统计接口
     */
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM tickets WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count == null ? 0 : count;
    }

    /**
     * 把结果集的一行映射成 Ticket 对象
     * SQLite 里 amount 是 NULL 时，getDouble 会返回 0，
     * 必须紧跟一次 wasNull() 判断，否则"没有金额"会被当成"金额为零"。
     */
    private Ticket mapRow(ResultSet rs, int rowNum) throws SQLException {
        double amountValue = rs.getDouble("amount");
        Double amount = rs.wasNull() ? null : amountValue;

        return new Ticket(
                rs.getLong("id"),
                rs.getString("ticket_no"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("applicant_no"),
                amount,
                rs.getString("status"),
                rs.getString("created_at"));
    }
}
