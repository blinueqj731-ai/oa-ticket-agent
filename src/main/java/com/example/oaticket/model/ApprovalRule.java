package com.example.oaticket.model;

/**
 * 审批规则类
 */

public record ApprovalRule (
        Long id,
        String ticketType,
        Double maxAmount,
        String requiredLevel,
        String description) {
}
