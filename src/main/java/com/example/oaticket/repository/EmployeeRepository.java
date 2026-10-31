package com.example.oaticket.repository;

import com.example.oaticket.model.Employee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * 员工数据访问
 */

@Repository
public class EmployeeRepository {

    private static final String SELECT_COLUMNS =
            "SELECT employee_no, name, department, level, manager_no FROM employees";

    private final JdbcTemplate jdbcTemplate;

    public EmployeeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Employee> findByEmployeeNo(String employeeNo) {
        String sql = SELECT_COLUMNS + " WHERE employee_no = ?";
        return jdbcTemplate.query(sql, this::mapRow, employeeNo)
                .stream()
                .findFirst();
    }

    private Employee mapRow(ResultSet rs, int rowNum) throws SQLException {
        // manager_no 对部门经理来说是 NULL，getString 遇 NULL 正好返回 null，不需要额外判断
        return new Employee(
                rs.getString("employee_no"),
                rs.getString("name"),
                rs.getString("department"),
                rs.getString("level"),
                rs.getString("manager_no"));
    }
}
