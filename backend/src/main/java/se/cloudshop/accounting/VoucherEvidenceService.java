package se.cloudshop.accounting;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VoucherEvidenceService {

  private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

  private final JournalEntryRepository journalEntryRepository;
  private final VoucherEvidenceRepository voucherEvidenceRepository;
  private final VoucherEvidenceContentRepository contentRepository;
  private final AccountingService accountingService;

  public VoucherEvidenceService(
      JournalEntryRepository journalEntryRepository,
      VoucherEvidenceRepository voucherEvidenceRepository,
      VoucherEvidenceContentRepository contentRepository,
      AccountingService accountingService
  ) {
    this.journalEntryRepository = journalEntryRepository;
    this.voucherEvidenceRepository = voucherEvidenceRepository;
    this.contentRepository = contentRepository;
    this.accountingService = accountingService;
  }

  @Transactional
  public VoucherEvidence attach(String voucherNumber, MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher document is required and must be at most 10 MB.");
    }

    String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim()
        .replaceAll("[\\r\\n\\\\/]", "_");
    if (fileName.isBlank() || fileName.length() > 255) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher document needs a valid file name.");
    }

    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read voucher document.");
    }
    if (content.length == 0 || content.length > MAX_FILE_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher document must be at most 10 MB.");
    }

    String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
    if (!contentType.equals(detectContentType(content)) || !hasMatchingExtension(fileName, contentType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher document must be a matching PDF, JPG, PNG or WebP file.");
    }

    List<JournalEntry> entries = journalEntryRepository.findByVoucherNumberForCorrection(voucherNumber);
    if (entries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found.");
    }
    if (entries.stream().anyMatch(entry -> !List.of("manual_voucher", "opening_balance").contains(entry.getSourceType()))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only manual or opening balance vouchers accept this document.");
    }
    if (entries.stream().anyMatch(entry -> !"missing_voucher_evidence".equals(entry.getEvidenceStatus()))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher already has archived evidence.");
    }
    entries.forEach(entry -> accountingService.requireUnlockedAccountingDate(entry.getVoucherDate()));

    VoucherEvidence evidence = voucherEvidenceRepository.saveAndFlush(new VoucherEvidence(
        voucherNumber, fileName, contentType, sha256(content), content));
    contentRepository.saveAndFlush(new VoucherEvidenceContent(evidence.getId(), content));
    entries.forEach(entry -> entry.attachVoucherEvidence(evidence));
    journalEntryRepository.saveAllAndFlush(entries);
    return evidence;
  }

  @Transactional(readOnly = true)
  public VoucherEvidence get(String voucherNumber) {
    return voucherEvidenceRepository.findByVoucherNumber(voucherNumber)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher document not found."));
  }

  @Transactional(readOnly = true)
  public DownloadedEvidence download(String voucherNumber) {
    VoucherEvidence evidence = get(voucherNumber);
    byte[] content = contentRepository.findById(evidence.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "Voucher document metadata exists but content is missing."))
        .getContent();
    if (content.length != evidence.getSize() || !sha256(content).equals(evidence.getSha256())) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Voucher document checksum does not match.");
    }
    return new DownloadedEvidence(evidence, content);
  }

  public record DownloadedEvidence(VoucherEvidence evidence, byte[] content) {
  }

  private String detectContentType(byte[] content) {
    if (startsWith(content, new byte[] {'%', 'P', 'D', 'F', '-'})) return "application/pdf";
    if (startsWith(content, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
    if (startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
    if (content.length >= 12 && startsWith(content, new byte[] {'R', 'I', 'F', 'F'})
        && Arrays.equals(Arrays.copyOfRange(content, 8, 12), new byte[] {'W', 'E', 'B', 'P'})) return "image/webp";
    return "";
  }

  private boolean startsWith(byte[] content, byte[] prefix) {
    if (content.length < prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) {
      if (content[index] != prefix[index]) return false;
    }
    return true;
  }

  private boolean hasMatchingExtension(String fileName, String contentType) {
    String name = fileName.toLowerCase(Locale.ROOT);
    return switch (contentType) {
      case "application/pdf" -> name.endsWith(".pdf");
      case "image/jpeg" -> name.endsWith(".jpg") || name.endsWith(".jpeg");
      case "image/png" -> name.endsWith(".png");
      case "image/webp" -> name.endsWith(".webp");
      default -> false;
    };
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }
}
