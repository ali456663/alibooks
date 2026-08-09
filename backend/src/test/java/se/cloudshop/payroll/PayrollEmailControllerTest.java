package se.cloudshop.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.email.PayrollEmailService;

class PayrollEmailControllerTest {

  private final JwtService jwtService = new JwtService("test_secret");
  private final AuthHeader authHeader = new AuthHeader(jwtService);
  private final PayrollEmailService payrollEmailService = mock(PayrollEmailService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final PayrollEmailController controller = new PayrollEmailController(
      authHeader,
      payrollEmailService,
      auditService
  );

  @Test
  void payslipPdfUsesSafeContentDispositionFilename() {
    PayrollPayslipEmailRequest request = payslipRequest("2026-07");
    when(payrollEmailService.createPayslipPdf(request)).thenReturn("%PDF".getBytes());
    when(payrollEmailService.payslipFilename(request)).thenReturn("lone\r\n/slip.pdf");

    ResponseEntity<byte[]> response = controller.createPayslipPdf("Bearer " + token(), request);

    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(contentDisposition).contains("attachment");
    assertThat(contentDisposition).doesNotContain("\r").doesNotContain("\n").doesNotContain("/");
    assertThat(contentDisposition).contains("lone___slip.pdf");
  }

  @Test
  void payslipArchiveUsesSafeContentDispositionFilename() {
    PayrollPayslipEmailRequest payslip = payslipRequest("2026-07");
    PayrollPayslipArchiveRequest request = new PayrollPayslipArchiveRequest("2026-07", List.of(payslip));
    when(payrollEmailService.createPayslipArchive(request)).thenReturn("zip".getBytes());
    when(payrollEmailService.payslipArchiveFilename("2026-07")).thenReturn("archive\r\n/evil.zip");

    ResponseEntity<byte[]> response = controller.createPayslipArchive("Bearer " + token(), request);

    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(contentDisposition).contains("attachment");
    assertThat(contentDisposition).doesNotContain("\r").doesNotContain("\n").doesNotContain("/");
    assertThat(contentDisposition).contains("archive___evil.zip");
  }

  private PayrollPayslipEmailRequest payslipRequest(String period) {
    return new PayrollPayslipEmailRequest(
        "employee@example.com",
        "Ali Wafa",
        "20010203-6598",
        "Byvagen 56",
        period,
        "2026-07-25",
        "L-2026-0001",
        "7010",
        "DRAFT",
        30000,
        9000,
        21000,
        9426,
        39426,
        "Lonebesked",
        "Hej"
    );
  }

  private String token() {
    return jwtService.createToken("ali@example.com");
  }
}
