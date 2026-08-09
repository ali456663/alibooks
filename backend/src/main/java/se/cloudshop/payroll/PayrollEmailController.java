package se.cloudshop.payroll;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.email.PayrollEmailService;

@RestController
public class PayrollEmailController {

  private final AuthHeader authHeader;
  private final PayrollEmailService payrollEmailService;
  private final AuditService auditService;

  public PayrollEmailController(
      AuthHeader authHeader,
      PayrollEmailService payrollEmailService,
      AuditService auditService
  ) {
    this.authHeader = authHeader;
    this.payrollEmailService = payrollEmailService;
    this.auditService = auditService;
  }

  @PostMapping("/payroll/payslip-email")
  public Map<String, String> sendPayslipEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody PayrollPayslipEmailRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    payrollEmailService.sendPayslip(request);
    auditService.record(
        "payroll",
        "payslip",
        safePeriod(request == null ? null : request.period()),
        "payslip_email_sent",
        safePeriod(request == null ? null : request.period()),
        "Payslip email sent.",
        request == null ? 0 : safeAmount(request.netPay()),
        authorizationHeader
    );
    return Map.of(
        "status", "sent",
        "recipientEmail", request.recipientEmail()
    );
  }

  @PostMapping(value = "/payroll/payslip-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> createPayslipPdf(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody PayrollPayslipEmailRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    byte[] pdf = payrollEmailService.createPayslipPdf(request);
    auditService.record(
        "export",
        "payslip_pdf",
        safePeriod(request == null ? null : request.period()),
        "payslip_pdf_exported",
        safePeriod(request == null ? null : request.period()),
        "Payslip PDF exported.",
        request == null ? 0 : safeAmount(request.netPay()),
        authorizationHeader
    );
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(payrollEmailService.payslipFilename(request)))
        .body(pdf);
  }

  @PostMapping(value = "/payroll/payslip-archive", produces = "application/zip")
  public ResponseEntity<byte[]> createPayslipArchive(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody PayrollPayslipArchiveRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    byte[] archive = payrollEmailService.createPayslipArchive(request);
    int payslipCount = request == null || request.payslips() == null ? 0 : request.payslips().size();
    long totalNetPay = request == null || request.payslips() == null
        ? 0L
        : request.payslips().stream().mapToLong(PayrollPayslipEmailRequest::netPay).sum();
    auditService.record(
        "export",
        "payslip_archive",
        safePeriod(request == null ? null : request.period()),
        "payslip_archive_exported",
        safePeriod(request == null ? null : request.period()),
        "Payslip archive exported. Payslips: " + payslipCount + ".",
        safeAmount(totalNetPay),
        authorizationHeader
    );
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(payrollEmailService.payslipArchiveFilename(request.period())))
        .body(archive);
  }

  private String attachmentDisposition(String filename) {
    return ContentDisposition.attachment()
        .filename(safeDownloadFilename(filename), StandardCharsets.UTF_8)
        .build()
        .toString();
  }

  private String safeDownloadFilename(String filename) {
    String clean = filename == null ? "" : filename.trim()
        .replace('\r', '_')
        .replace('\n', '_')
        .replace('\\', '_')
        .replace('/', '_');
    return clean.isBlank() ? "payroll-export" : clean;
  }

  private String safePeriod(String period) {
    return period == null || period.isBlank() ? "unknown-period" : period.trim();
  }

  private int safeAmount(long amount) {
    if (amount > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (amount < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) amount;
  }
}
