package com.example.oaticket.tool;

import com.example.oaticket.model.ApprovalRule;
import com.example.oaticket.model.Employee;
import com.example.oaticket.model.Ticket;
import com.example.oaticket.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三个查询工具的测试。
 *
 * 这一步故意不接大模型。原因：如果工具本身查错了数据，模型再怎么聪明也是错的。
 * 先把工具单独测通，等接入模型之后如果结果异常，就能确定问题在调用环节而不是数据查询。
 *
 * @SpringBootTest 会启动完整的 Spring 上下文（含数据库初始化和依赖注入），
 * 所以能直接注入 Tool 对象来调。
 */
@SpringBootTest
@DisplayName("三个数据查询工具")
class ToolTest {

    @Autowired
    private TicketTool ticketTool;

    @Autowired
    private EmployeeTool employeeTool;

    @Autowired
    private RuleTool ruleTool;

    // ==================== 工单查询 ====================

    @Test
    @DisplayName("按编号能查到工单，关键字段完整")
    void getTicket_shouldReturnFullTicket() {
        ToolResult<Ticket> result = ticketTool.getTicket("T-20261001-001");

        assertTrue(result.success(), "查询应该成功");
        Ticket ticket = result.data();
        assertNotNull(ticket);
        assertEquals("报销", ticket.type());
        assertEquals("E001", ticket.applicantNo());
        assertEquals(4800.0, ticket.amount(), 0.001);
        assertEquals("PENDING", ticket.status());
    }

    @Test
    @DisplayName("请假工单金额必须是 null，不能变成 0")
    void getTicket_leaveTicket_amountShouldBeNull() {
        ToolResult<Ticket> result = ticketTool.getTicket("T-20261001-002");

        assertTrue(result.success());
        // 这条断言防的是 JDBC 的坑：getDouble 遇 NULL 返回 0 而不报错，
        // 如果不做 wasNull 判断，"没有金额"会静悄悄变成"金额为零"
        assertNull(result.data().amount(), "请假单不该有金额，注意这里不能是 0");
    }

    @Test
    @DisplayName("查不存在的工单要返回失败和原因，不能给 null")
    void getTicket_notFound_shouldFailWithReason() {
        ToolResult<Ticket> result = ticketTool.getTicket("T-99999999-999");

        assertFalse(result.success());
        assertNull(result.data());
        assertTrue(result.message().contains("未找到"), "失败原因要让模型看得懂");
    }

    @Test
    @DisplayName("空工单号要在入口拦下，不带着脏参数查库")
    void getTicket_blankArgument_shouldFail() {
        assertFalse(ticketTool.getTicket("   ").success());
        assertFalse(ticketTool.getTicket(null).success());
        assertFalse(ticketTool.getTicket("").success());
    }

    // ==================== 员工查询 ====================

    @Test
    @DisplayName("按工号能查到员工，部门与上级正确")
    void getEmployee_shouldReturnEmployee() {
        ToolResult<Employee> result = employeeTool.getEmployee("E001");

        assertTrue(result.success());
        Employee employee = result.data();
        assertEquals("张伟", employee.name());
        assertEquals("技术部", employee.department());
        assertEquals("E002", employee.managerNo());
    }

    @Test
    @DisplayName("部门经理没有上级，managerNo 应为 null")
    void getEmployee_departmentManager_hasNoManager() {
        ToolResult<Employee> result = employeeTool.getEmployee("E002");

        assertTrue(result.success());
        assertNull(result.data().managerNo(), "部门经理是审批链顶端，不该有上级");
    }

    @Test
    @DisplayName("查不存在的员工要返回失败")
    void getEmployee_notFound_shouldFail() {
        ToolResult<Employee> result = employeeTool.getEmployee("E999");

        assertFalse(result.success());
        assertTrue(result.message().contains("未找到"));
    }

    // ==================== 规则查询 ====================

    @Test
    @DisplayName("报销类返回三条候选规则")
    void getRules_expense_shouldReturnThreeRules() {
        ToolResult<List<ApprovalRule>> result = ruleTool.getRules("报销");

        assertTrue(result.success());
        assertEquals(3, result.data().size(), "差旅费、办公用品、招待费各一条");
    }

    @Test
    @DisplayName("报销规则带金额上限，请假规则不限额")
    void getRules_amountLimitVariesByType() {
        ToolResult<List<ApprovalRule>> expenseRules = ruleTool.getRules("报销");
        assertTrue(expenseRules.data().get(0).maxAmount() != null, "报销类应有金额上限");

        ToolResult<List<ApprovalRule>> leaveRules = ruleTool.getRules("请假");
        assertNull(leaveRules.data().get(0).maxAmount(), "请假类不该有金额上限");
    }

    @Test
    @DisplayName("不存在的类型要返回失败并提示可选值")
    void getRules_unknownType_shouldFailWithHint() {
        ToolResult<List<ApprovalRule>> result = ruleTool.getRules("不存在的类型");

        assertFalse(result.success());
        assertTrue(result.message().contains("没有找到"));
        assertTrue(result.message().contains("报销"), "失败信息里要带上可选值，方便模型自我纠正");
    }
}
