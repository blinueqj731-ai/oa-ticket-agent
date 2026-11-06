package com.example.oaticket.rag;

import com.example.oaticket.model.Ticket;
import com.example.oaticket.repository.TicketEmbeddingRepository;
import com.example.oaticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把工单灌成向量
 *
 * 只在索引为空时执行一次。20 条工单大约两次 embedding 调用，几百毫秒。
 * 工单新增后索引不会自动更新——真上生产得做成"写入时同步更新索引"的增量方案
 */
@Component
public class TicketIndexInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TicketIndexInitializer.class);

    private final TicketRepository ticketRepository;
    private final TicketEmbeddingRepository embeddingRepository;
    private final EmbeddingClient embeddingClient;

    public TicketIndexInitializer(TicketRepository ticketRepository,
                                  TicketEmbeddingRepository embeddingRepository,
                                  EmbeddingClient embeddingClient) {
        this.ticketRepository = ticketRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void run(String... args) {
        try {
            int existing = embeddingRepository.count();
            if (existing > 0) {
                log.info("[索引] 已存在 {} 条工单向量，跳过构建", existing);
                return;
            }

            List<Ticket> tickets = ticketRepository.findAll();
            if (tickets.isEmpty()) {
                log.info("[索引] 没有工单数据，跳过");
                return;
            }

            // 类型放最前面：让"报销""请假"这类词在向量里权重足够，
            // 检索同类工单时类型是最强的区分信号
            List<String> texts = tickets.stream()
                    .map(t -> t.type() + "｜" + t.title() + "｜" + t.content())
                    .toList();

            List<float[]> vectors = embeddingClient.embedBatch(texts);
            for (int i = 0; i < tickets.size(); i++) {
                embeddingRepository.save(tickets.get(i).ticketNo(), texts.get(i), vectors.get(i));
            }
            log.info("[索引] 构建完成，共 {} 条工单向量，维度 {}", tickets.size(), vectors.get(0).length);
        } catch (Exception e) {
            // 没配 Key 或断网时不能让项目起不来：
            // 相似工单检索失效，但其余四个工具照常可用
            log.warn("[索引] 构建失败，相似工单检索将不可用：{}", e.getMessage());
        }
    }
}
