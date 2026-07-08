package se.cloudshop.payroll;

public record PayrollPayslipEmailRequest(
    String recipientEmail,
    String employeeName,
    String period,
    String subject,
    String body
) {
}
