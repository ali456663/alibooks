package se.cloudshop.accounting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;

@RestController
public class VoucherEvidenceController {

  private final AuthHeader authHeader;
  private final AccountingService accountingService;
  private final VoucherEvidenceService evidenceService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public VoucherEvidenceController(
      AuthHeader authHeader,
      AccountingService accountingService,
      VoucherEvidenceService evidenceService,
      AuditService auditService,
      ObjectMapper objectMapper
  ) {
    this.authHeader = authHeader;
    this.accountingService = accountingService;
    this.evidenceService = evidenceService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @PostMapping(value = "/journal-entries/manual-with-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public List<JournalEntry> createManualVoucher(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestPart("request") String requestJson,
      @RequestPart("file") MultipartFile file
  ) {
    authHeader.requireValidToken(authorization);
    CreateManualJournalEntryRequest request = parseRequest(requestJson, CreateManualJournalEntryRequest.class);
    List<JournalEntry> entries = accountingService.createManualEntry(request);
    String voucherNumber = entries.get(0).getVoucherNumber();
    VoucherEvidence evidence = evidenceService.attach(voucherNumber, file);
    auditService.record("voucher", "journal_entry", voucherNumber, "manual_created", voucherNumber,
        "Manual voucher created with archived document", request.amount(), authorization);
    auditEvidence(evidence, authorization);
    return entries;
  }

  @PostMapping(value = "/journal-entries/manual-multi-with-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public List<JournalEntry> createManualMultiVoucher(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestPart("request") String requestJson,
      @RequestPart("file") MultipartFile file
  ) {
    authHeader.requireValidToken(authorization);
    CreateManualMultiLineJournalEntryRequest request = parseRequest(requestJson, CreateManualMultiLineJournalEntryRequest.class);
    List<JournalEntry> entries = accountingService.createManualMultiLineEntry(request);
    String voucherNumber = entries.getFirst().getVoucherNumber();
    VoucherEvidence evidence = evidenceService.attach(voucherNumber, file);
    int amount = Math.toIntExact(entries.stream().mapToLong(JournalEntry::getDebit).sum());
    auditService.record("voucher", "journal_entry", voucherNumber, "manual_multi_created", voucherNumber,
        "Manual multi-line voucher created with archived document", amount, authorization);
    auditEvidence(evidence, authorization);
    return entries;
  }

  @PostMapping(value = "/journal-entries/opening-balance-with-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public List<JournalEntry> createOpeningBalanceVoucher(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestPart("request") String requestJson,
      @RequestPart("file") MultipartFile file
  ) {
    authHeader.requireValidToken(authorization);
    CreateOpeningBalanceRequest request = parseRequest(requestJson, CreateOpeningBalanceRequest.class);
    List<JournalEntry> entries = accountingService.createOpeningBalanceEntry(request);
    String voucherNumber = entries.getFirst().getVoucherNumber();
    VoucherEvidence evidence = evidenceService.attach(voucherNumber, file);
    int amount = Math.toIntExact(entries.stream().mapToLong(JournalEntry::getDebit).sum());
    auditService.record("voucher", "journal_entry", voucherNumber, "opening_balance_created", voucherNumber,
        "Opening balance voucher created with archived document", amount, authorization);
    auditEvidence(evidence, authorization);
    return entries;
  }

  @PostMapping(value = "/journal-entries/{voucherNumber}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public EvidenceInfo attach(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String voucherNumber,
      @RequestPart("file") MultipartFile file
  ) {
    authHeader.requireValidToken(authorization);
    VoucherEvidence evidence = evidenceService.attach(voucherNumber, file);
    auditEvidence(evidence, authorization);
    return info(evidence);
  }

  @GetMapping("/journal-entries/{voucherNumber}/evidence")
  public ResponseEntity<byte[]> download(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String voucherNumber
  ) {
    authHeader.requireValidToken(authorization);
    VoucherEvidenceService.DownloadedEvidence document = evidenceService.download(voucherNumber);
    VoucherEvidence evidence = document.evidence();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(evidence.getFileName(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
        .contentType(MediaType.parseMediaType(evidence.getContentType()))
        .body(document.content());
  }

  private void auditEvidence(VoucherEvidence evidence, String authorization) {
    auditService.record("voucher", "voucher_evidence", evidence.getVoucherNumber(), "document_uploaded",
        evidence.getFileName(), "Archived voucher document SHA-256: " + evidence.getSha256(), 0, authorization);
  }

  private EvidenceInfo info(VoucherEvidence evidence) {
    return new EvidenceInfo(evidence.getVoucherNumber(), evidence.getFileName(), evidence.getContentType(),
        evidence.getSha256(), evidence.getUploadedAt().toString(), evidence.getSize());
  }

  private <T> T parseRequest(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid voucher request.");
    }
  }

  public record EvidenceInfo(String voucherNumber, String fileName, String contentType, String sha256,
                             String uploadedAt, int size) {
  }
}
