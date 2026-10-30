package com.example.oaticket.controller;

import com.example.oaticket.model.Ticket;
import com.example.oaticket.repository.TicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单查询接口
 */

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * GET /api/tickets
     * 列出全部工单
     * @return 工单列表；没有任何工单时返回空数组，不会返回 null
     */
    @GetMapping
    public List<Ticket> listAll() {
        return ticketRepository.findAll();
    }

    /**
     * GET /api/tickets/{ticketNo}
     * 按编号查一张工单，查不到返回
     * @param ticketNo 工单编号：格式为 T-YYYYMMDD-NNN，例如 T-20261001-001
     * @return 查到返回 HTTP 200 和工单详情；编号不存在返回 HTTP 404 空响应
     */
    @GetMapping("/{ticketNo}")
    public ResponseEntity<Ticket> detail(@PathVariable String ticketNo) {
        return ticketRepository.findByTicketNo(ticketNo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
