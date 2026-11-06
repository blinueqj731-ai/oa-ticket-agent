package com.example.oaticket.tool;

import com.example.oaticket.client.dto.ToolCall;
import com.example.oaticket.client.dto.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 *
 * 负责两件事：把工具定义交给模型，以及按模型的选择分发到具体方法
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final TicketTool ticketTool;
    private final EmployeeTool employeeTool;
    private final RuleTool ruleTool;
    private final TicketActionTool ticketActionTool;
    private final SimilarTicketTool similarTicketTool;
    private final ObjectMapper objectMapper;

    public ToolRegistry(TicketTool ticketTool, EmployeeTool employeeTool,
                        RuleTool ruleTool, TicketActionTool ticketActionTool,
                        SimilarTicketTool similarTicketTool, ObjectMapper objectMapper) {
        this.ticketTool = ticketTool;
        this.employeeTool = employeeTool;
        this.ruleTool = ruleTool;
        this.ticketActionTool = ticketActionTool;
        this.similarTicketTool = similarTicketTool;
        this.objectMapper = objectMapper;
    }

    /**
     * 提供给模型的工具定义
     *
     * description 写得好不好，直接决定模型会不会在正确的时机调对工具
     *
     * @return 五个工具的定义列表
     */
    public List<ToolDefinition> definitions() {
        return List.of(
                ToolDefinition.function(
                        "get_ticket",
                        "根据工单编号查询工单详情，返回类型、标题、内容、申请人工号、金额和当前状态。处理任何工单问题前都应先调用",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "ticketNo", Map.of("type", "string",
                                                "description", "工单编号，格式 T-YYYYMMDD-NNN，例如 T-20261001-001")),
                                "required", List.of("ticketNo"))),
                ToolDefinition.function(
                        "get_employee",
                        "根据员工工号查询员工的姓名、部门、职级和直属上级工号。需要判断审批人时调用",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "employeeNo", Map.of("type", "string",
                                                "description", "员工工号，格式 E001")),
                                "required", List.of("employeeNo"))),
                ToolDefinition.function(
                        "get_rules",
                        "根据工单类型查询该类型的全部审批规则，包括金额上限、需要哪一级审批、规则说明。判断审批要求时调用",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "ticketType", Map.of("type", "string",
                                                "description", "工单类型，只能是：报销、请假、权限申请、设备报修")),
                                "required", List.of("ticketType"))),
                ToolDefinition.function(
                        "propose_ticket_decision",
                        "提交工单状态变更提案。注意这个工具只生成待确认的提案，不会真正修改工单，"
                                + "必须由人工调用确认接口后才生效。用户要求通过或驳回某张工单时使用",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "ticketNo", Map.of("type", "string",
                                                "description", "工单编号，格式 T-YYYYMMDD-NNN"),
                                        "decision", Map.of("type", "string",
                                                "enum", List.of("APPROVED", "REJECTED"),
                                                "description", "目标状态：APPROVED 表示通过，REJECTED 表示驳回"),
                                        "reason", Map.of("type", "string",
                                                "description", "变更理由，会写入审计日志，不能为空")),
                                "required", List.of("ticketNo", "decision", "reason"))),
                ToolDefinition.function(
                        "find_similar_tickets",
                        "按问题描述检索历史相似工单，返回单号、类型、标题、金额、状态和相似度。"
                                + "需要参考「以前类似单子怎么批的」时调用",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string",
                                                "description", "工单标题或内容摘要，例如「差旅费 4800 元 住宿 报销」")),
                                "required", List.of("query"))));
    }

    /**
     * 执行模型请求的工具调用
     *
     * @param toolCall 模型返回的调用请求
     * @return 工具执行结果的 JSON 字符串，出错时返回带错误说明的 JSON
     */
    public String execute(ToolCall toolCall) {
        String name = toolCall.function().name();
        String rawArguments = toolCall.function().arguments();
        log.info("模型请求调用工具 name={} arguments={}", name, rawArguments);

        try {
            Map<String, Object> args = objectMapper.readValue(
                    rawArguments, new TypeReference<Map<String, Object>>() {
                    });

            Object result = switch (name) {
                case "get_ticket" -> ticketTool.getTicket(text(args, "ticketNo"));
                case "get_employee" -> employeeTool.getEmployee(text(args, "employeeNo"));
                case "get_rules" -> ruleTool.getRules(text(args, "ticketType"));
                case "propose_ticket_decision" -> ticketActionTool.proposeDecision(
                        text(args, "ticketNo"), text(args, "decision"), text(args, "reason"));
                case "find_similar_tickets" -> similarTicketTool.findSimilarTickets(text(args, "query"));
                default -> Map.of("success", false, "message", "不存在名为 " + name + " 的工具");
            };
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            // 参数解析失败或工具抛异常时，把原因交回给模型，让它自己决定换参数重试还是如实告知
            log.warn("工具执行失败 name={} 原因={}", name, e.getMessage());
            return "{\"success\":false,\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String text(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString();
    }
}
