package com.example.oaticket.repository;

import com.example.oaticket.model.OperationLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 操作日志数据访问
 *
 * 所有改变工单状态的动作都必须在这里留一条，事后能查到是谁在什么时间改的
 */
@Repository
public class OperationLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public OperationLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String ticketNo, String action, String operator, String detail, String createdAt) {
        jdbcTemplate.update(
                "INSERT INTO operation_logs (ticket_no, action, operator, detail, created_at) VALUES (?,?,?,?,?)",
                ticketNo, action, operator, detail, createdAt);
    }

    public List<OperationLog> findByTicketNo(String ticketNo) {
        return jdbcTemplate.query(
                "SELECT id, ticket_no, action, operator, detail, created_at FROM operation_logs "
                        + "WHERE ticket_no = ? ORDER BY id DESC",
                (rs, rowNum) -> new OperationLog(
                        rs.getLong("id"),
                        rs.getString("ticket_no"),
                        rs.getString("action"),
                        rs.getString("operator"),
                        rs.getString("detail"),
                        rs.getString("created_at")),
                ticketNo);
    }
}
