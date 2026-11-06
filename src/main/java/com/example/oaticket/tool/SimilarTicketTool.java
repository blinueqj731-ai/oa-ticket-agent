package com.example.oaticket.tool;

import com.example.oaticket.config.EmbeddingProperties;
import com.example.oaticket.model.SimilarTicket;
import com.example.oaticket.model.Ticket;
import com.example.oaticket.model.ToolResult;
import com.example.oaticket.rag.EmbeddingClient;
import com.example.oaticket.repository.TicketEmbeddingRepository;
import com.example.oaticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 工具：按问题描述检索历史相似工单
 *
 * 为什么需要它：get_rules 查得到"差旅费上限 5000 元"，但查不到
 * "上个月有个 4800 的差旅单是怎么批的"。审批人真正想参考的是后者
 */
@Component
public class SimilarTicketTool {

    private static final Logger log = LoggerFactory.getLogger(SimilarTicketTool.class);

    private final EmbeddingClient embeddingClient;
    private final TicketEmbeddingRepository embeddingRepository;
    private final TicketRepository ticketRepository;
    private final EmbeddingProperties properties;

    public SimilarTicketTool(EmbeddingClient embeddingClient,
                             TicketEmbeddingRepository embeddingRepository,
                             TicketRepository ticketRepository,
                             EmbeddingProperties properties) {
        this.embeddingClient = embeddingClient;
        this.embeddingRepository = embeddingRepository;
        this.ticketRepository = ticketRepository;
        this.properties = properties;
    }

    /**
     * 按描述找相似的历史工单
     *
     * @param query 工单标题或内容摘要，如"差旅费 4800 元住宿报销"
     * @return 相似工单列表，按相似度降序；没有足够相似的返回失败说明
     */
    public ToolResult<List<SimilarTicket>> findSimilarTickets(String query) {
        if (query == null || query.isBlank()) {
            return ToolResult.fail("查询描述不能为空，请传入工单标题或内容摘要");
        }

        try {
            List<TicketEmbeddingRepository.StoredVector> stored = embeddingRepository.findAll();
            if (stored.isEmpty()) {
                return ToolResult.fail("历史工单索引为空，暂时无法检索相似工单，请参考审批规则判断");
            }

            float[] queryVector = embeddingClient.embed(query.trim());

            List<Scored> scored = new ArrayList<>(stored.size());
            for (TicketEmbeddingRepository.StoredVector item : stored) {
                double similarity = cosine(queryVector, item.vector());
                if (similarity >= properties.minScore()) {
                    scored.add(new Scored(item.ticketNo(), similarity));
                }
            }

            if (scored.isEmpty()) {
                // 明确说查不到，模型才不会拿不相关的东西硬凑
                return ToolResult.fail("未找到相似度足够的历史工单，请依据审批规则判断");
            }

            scored.sort(Comparator.comparingDouble(Scored::similarity).reversed());

            List<SimilarTicket> result = new ArrayList<>();
            for (Scored item : scored.subList(0, Math.min(properties.topK(), scored.size()))) {
                Ticket ticket = ticketRepository.findByTicketNo(item.ticketNo()).orElse(null);
                if (ticket == null) {
                    continue;
                }
                result.add(new SimilarTicket(
                        ticket.ticketNo(),
                        ticket.type(),
                        ticket.title(),
                        ticket.amount(),
                        ticket.status(),
                        Math.round(item.similarity() * 100) / 100.0));
            }
            log.info("相似工单检索 query={} 命中={} 条", query, result.size());
            return result.isEmpty()
                    ? ToolResult.fail("未找到相似的历史工单")
                    : ToolResult.ok(result);
        } catch (Exception e) {
            // 向量化失败（网络、密钥）时不把异常抛给上层，
            // 让模型拿到一句人话，它还能用其他工具把活干完
            log.warn("相似工单检索失败 query={} 原因={}", query, e.getMessage());
            return ToolResult.fail("相似工单检索失败：" + e.getMessage());
        }
    }

    /**
     * 余弦相似度
     *
     * 用余弦不用欧氏距离：文本向量关心方向不关心长度，
     * 长短两段话说的是一件事，夹角仍然很小
     */
    private double cosine(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Scored(String ticketNo, double similarity) {
    }
}
