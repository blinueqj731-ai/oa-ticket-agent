package com.example.oaticket.model;

/**
 * 相似历史工单
 *
 * similarity 是余弦相似度（0~1），保留两位小数。
 * 这个值要一起交给模型：0.9 和 0.5 的可参考程度完全不同，
 * 看到数字它才知道该多大程度参考这条历史记录
 */
public record SimilarTicket(
        String ticketNo,
        String type,
        String title,
        Double amount,
        String status,
        double similarity) {
}
