package com.example.oaticket.tool;

import com.example.oaticket.model.Ticket;
import com.example.oaticket.model.ToolResult;
import com.example.oaticket.repository.TicketRepository;
import org.springframework.stereotype.Component;

/**
 * 工具：按工单编号查询工单详情
 *
 * 这个类会被注册给大模型调用，所以它的方法签名要足够"笨"：
 * 参数只有字符串，返回值是结构化的 JSON，模型一看就懂怎么用。
 * 复杂的业务逻辑不要塞进来，模型理解不了的复杂度最后都会变成调用失败。
 */

@Component
public class TicketTool {

    private final TicketRepository ticketRepository;

    public TicketTool(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * @param ticketNo 工单编号，格式 T-YYYYMMDD-NNN
     */
    public ToolResult<Ticket> getTicket(String ticketNo) {
        // 模型偶尔会传空字符串或者空格，先挡掉，避免带着脏参数去查库
        if (ticketNo == null || ticketNo.isBlank()) {
            return ToolResult.fail("工单编号不能为空，格式应为 T-YYYYMMDD-NNN");
        }

        return ticketRepository.findByTicketNo(ticketNo.trim())
                .map(ticket -> ToolResult.ok(ticket))
                .orElse(ToolResult.fail("未找到编号为 " + ticketNo.trim() + " 的工单"));
    }
}
