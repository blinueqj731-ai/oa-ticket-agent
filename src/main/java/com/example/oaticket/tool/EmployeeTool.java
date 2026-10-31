package com.example.oaticket.tool;

import com.example.oaticket.model.Employee;
import com.example.oaticket.model.ToolResult;
import com.example.oaticket.repository.EmployeeRepository;
import org.springframework.stereotype.Component;

/**
 * 工具：按工号查询员工信息
 *
 * 审批场景里查员工是为了两件事：确认部门（决定谁有审批权）、确认直属上级（建议里要写清找谁批）。
 */

@Component
public class EmployeeTool {

    private final EmployeeRepository employeeRepository;

    public EmployeeTool(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * @param employeeNo 员工工号，格式 E001
     */
    public ToolResult<Employee> getEmployee(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return ToolResult.fail("员工工号不能为空，格式应为 E001 这样的形式");
        }

        return employeeRepository.findByEmployeeNo(employeeNo.trim())
                .map(employee -> ToolResult.ok(employee))
                .orElse(ToolResult.fail("未找到工号为 " + employeeNo.trim() + " 的员工"));
    }
}
