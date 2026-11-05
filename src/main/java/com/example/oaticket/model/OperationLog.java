package com.example.oaticket.model;

/**
 * 工单操作日志
 */
public record OperationLog(
        Long id,
        String ticketNo,
        String action,
        String operator,
        String detail,
        String createdAt) {
}
