package se.cloudshop.payroll;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.email.PayrollEmailService;

@RestController
public class PayrollEmailController {

  private final AuthHeader authHeader;
  private final PayrollEmailService payrollEmailService;

  public PayrollEmailController(
      AuthHeader authHeader,
      PayrollEmailService payrollEmailService
  ) {
    this.authHeader = authHeader;
    this.payrollEmailService = payrollEmailService;
  }

  @PostMapping("/payroll/payslip-email")
  public Map<String, String> sendPayslipEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody PayrollPayslipEmailRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    payrollEmailService.sendPayslip(request);
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
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payrollEmailService.payslipFilename(request) + "\"")
        .body(pdf);
  }
}
