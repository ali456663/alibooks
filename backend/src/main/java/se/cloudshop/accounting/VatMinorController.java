package se.cloudshop.accounting;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;

@RestController
public class VatMinorController {
  private final VatMinorService service;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public VatMinorController(VatMinorService service, AuthHeader authHeader, AuditService audit) {
    this.service = service;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/v2/vat-report")
  public VatMinorReport report(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    authHeader.requireValidToken(authorization);
    return service.create(from, to);
  }

  @GetMapping("/v2/vat-report/export")
  public ResponseEntity<byte[]> export(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    VatMinorReport report = report(authorization, from, to);
    String csv = "PeriodFrom,PeriodTo,Currency,MinorUnitExponent,SalesBase25Minor,OutputVat25Minor,"
        + "SalesBase12Minor,OutputVat12Minor,SalesBase6Minor,OutputVat6Minor,"
        + "OutputVatMinor,InputVatMinor,VatToPayMinor,Settled\n"
        + (from == null ? "" : from) + "," + (to == null ? "" : to) + ","
        + report.currencyCode() + "," + report.minorUnitExponent() + ","
        + report.salesBase25Minor() + "," + report.outputVat25Minor() + ","
        + report.salesBase12Minor() + "," + report.outputVat12Minor() + ","
        + report.salesBase6Minor() + "," + report.outputVat6Minor() + ","
        + report.outputVatMinor() + "," + report.inputVatMinor() + ","
        + report.vatToPayMinor() + "," + report.settled() + "\n";
    String period = (from == null ? "" : from) + " - " + (to == null ? "" : to);
    audit.record("export", "vat_report", period, "vat_minor_report_exported", period,
        "VAT ledger basis exported in minor units (SEK, exponent 2).", 1, authorization);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vat-report-minor.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }
}
