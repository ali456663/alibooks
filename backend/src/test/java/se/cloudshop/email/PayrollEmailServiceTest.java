package se.cloudshop.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.payroll.PayrollPayslipArchiveRequest;
import se.cloudshop.payroll.PayrollPayslipEmailRequest;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

class PayrollEmailServiceTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final PayrollEmailService service = new PayrollEmailService(
      mailSender,
      settingsService,
      "smtp.example.com",
      "sender@example.com"
  );

  @Test
  void rejectsInvalidPayrollMathBeforeCreatingPdf() {
    PayrollPayslipEmailRequest request = payslip(20_000, 6_000, 15_000, 6_284, 26_284);

    assertThatThrownBy(() -> service.createPayslipPdf(request))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getReason())
                .isEqualTo("Net pay must equal gross salary minus withheld tax."));
  }

  @Test
  void rejectsInvalidPayrollPeriodBeforeCreatingPdf() {
    PayrollPayslipEmailRequest request = new PayrollPayslipEmailRequest(
        "employee@example.com",
        "Ali Wafa",
        "",
        "",
        "2026-13",
        "2026-07-25",
        "L-2026-0001",
        "7010",
        "Utkast",
        30_000,
        9_000,
        21_000,
        9_426,
        39_426,
        "Lonebesked",
        "Hej"
    );

    assertThatThrownBy(() -> service.createPayslipPdf(request))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getReason())
                .isEqualTo("Payslip period must use YYYY-MM format."));
  }

  @Test
  void rejectsPayslipFromAnotherPeriodInArchive() {
    PayrollPayslipEmailRequest request = payslip(30_000, 9_000, 21_000, 9_426, 39_426);

    assertThatThrownBy(() -> service.createPayslipArchive(new PayrollPayslipArchiveRequest(
        "2026-08",
        List.of(request)
    )))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getReason()).isEqualTo("Every payslip must match the archive period."));
  }

  @Test
  void neutralizesSpreadsheetFormulaInArchiveManifest() throws Exception {
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());
    PayrollPayslipEmailRequest request = new PayrollPayslipEmailRequest(
        "employee@example.com",
        " =HYPERLINK(\"https://example.com\",\"open\")",
        "",
        "",
        "2026-07",
        "2026-07-25",
        "L-2026-0001",
        "7010",
        "Utkast",
        30_000,
        9_000,
        21_000,
        9_426,
        39_426,
        "Lonebesked",
        "Hej"
    );

    byte[] archive = service.createPayslipArchive(new PayrollPayslipArchiveRequest(
        "2026-07",
        List.of(request)
    ));

    assertThat(readManifest(archive)).contains("' =HYPERLINK(");
  }

  @Test
  void rejectsPayrollCostOverflow() {
    assertThatThrownBy(() -> service.createPayslipPdf(
        payslip(Long.MAX_VALUE, 0, Long.MAX_VALUE, 1, Long.MIN_VALUE)))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getReason()).isEqualTo("Total payroll cost exceeds the supported amount."));
  }

  @Test
  void acceptsConsistentPayrollValuesAndCreatesPdf() {
    when(settingsService.getSettings()).thenReturn(AppSettings.defaults());

    byte[] pdf = service.createPayslipPdf(payslip(30_000, 9_000, 21_000, 9_426, 39_426));

    assertThat(pdf).startsWith("%PDF".getBytes());
  }

  private PayrollPayslipEmailRequest payslip(long gross, long tax, long net, long employerFee, long totalCost) {
    return new PayrollPayslipEmailRequest(
        "employee@example.com",
        "Ali Wafa",
        "",
        "",
        "2026-07",
        "2026-07-25",
        "L-2026-0001",
        "7010",
        "Utkast",
        gross,
        tax,
        net,
        employerFee,
        totalCost,
        "Lonebesked",
        "Hej"
    );
  }

  private String readManifest(byte[] archive) throws Exception {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if ("manifest.csv".equals(entry.getName())) {
          return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    throw new AssertionError("Archive manifest is missing.");
  }
}
