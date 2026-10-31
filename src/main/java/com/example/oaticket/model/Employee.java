package com.example.oaticket.model;

/**
 * 员工类
 */

public record Employee(
        String employeeNo,
        String name,
        String department,
        String level,
        String managerNo) {
}
