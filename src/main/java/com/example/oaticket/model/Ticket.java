package com.example.oaticket.model;

/**
 * 工单类
 */

public record Ticket(
        Long id,
        String ticketNo,
        String type,
        String title,
        String content,
        String applicantNo,
        Double amount,
        String status,
        String createdAt) {
}
