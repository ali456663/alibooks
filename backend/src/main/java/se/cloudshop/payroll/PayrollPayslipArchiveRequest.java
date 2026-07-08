package se.cloudshop.payroll;

import java.util.List;

public record PayrollPayslipArchiveRequest(
    String period,
    List<PayrollPayslipEmailRequest> payslips
) {
}
