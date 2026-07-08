package se.cloudshop.payroll;

public record PayrollPayslipEmailRequest(
    String recipientEmail,
    String employeeName,
    String personalNumber,
    String address,
    String period,
    String paymentDate,
    String voucherNumber,
    String salaryAccount,
    String status,
    long grossSalary,
    long withheldTax,
    long netPay,
    long employerFee,
    long totalCost,
    String subject,
    String body
) {
}
