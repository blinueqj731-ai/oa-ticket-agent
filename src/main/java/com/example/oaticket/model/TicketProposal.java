package com.example.oaticket.model;

/**
 * 待确认的工单状态变更提案
 *
 * 模型产出的只是一条建议，不是变更本身。人工确认前，工单状态一个字都不会动
 */
public record TicketProposal(
        Long id,
        String proposalId,
        String ticketNo,
        String fromStatus,
        String toStatus,
        String reason,
        String status,
        String createdAt,
        String decidedAt,
        String operator) {

    /** 待人工确认 */
    public static final String STATUS_PENDING = "PENDING";

    /** 已确认，工单状态已变更 */
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    /** 被人工驳回，工单未变更 */
    public static final String STATUS_REJECTED = "REJECTED";
}
