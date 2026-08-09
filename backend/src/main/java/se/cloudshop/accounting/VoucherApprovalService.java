package se.cloudshop.accounting;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VoucherApprovalService {

  private final VoucherApprovalRepository voucherApprovalRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final AccountingService accountingService;

  public VoucherApprovalService(
      VoucherApprovalRepository voucherApprovalRepository,
      JournalEntryRepository journalEntryRepository,
      AccountingService accountingService
  ) {
    this.voucherApprovalRepository = voucherApprovalRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.accountingService = accountingService;
  }

  public List<VoucherApproval> findAll() {
    return voucherApprovalRepository.findAllByOrderByReviewedAtDesc();
  }

  @Transactional
  public VoucherApproval update(String voucherNumber, UpdateVoucherApprovalRequest request) {
    String cleanVoucherNumber = clean(voucherNumber);
    if (cleanVoucherNumber.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher number is required.");
    }
    requireVoucherApprovalEditable(cleanVoucherNumber);

    String status = normalizeStatus(request == null ? null : request.status());
    String note = clean(request == null ? "" : request.note());
    String requestedReviewer = clean(request == null ? "" : request.reviewer());
    String reviewer = requestedReviewer.isBlank() ? "AliBooks" : requestedReviewer;

    VoucherApproval approval = voucherApprovalRepository.findByVoucherNumber(cleanVoucherNumber)
        .orElseGet(() -> new VoucherApproval(cleanVoucherNumber, status, note, reviewer));
    approval.update(status, note, reviewer);

    return voucherApprovalRepository.save(approval);
  }

  @Transactional
  public void delete(String voucherNumber) {
    String cleanVoucherNumber = clean(voucherNumber);
    if (cleanVoucherNumber.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher number is required.");
    }
    requireVoucherApprovalEditable(cleanVoucherNumber);

    voucherApprovalRepository.deleteByVoucherNumber(cleanVoucherNumber);
  }

  private void requireVoucherApprovalEditable(String voucherNumber) {
    journalEntryRepository.findByVoucherNumber(voucherNumber).stream()
        .map(JournalEntry::getVoucherDate)
        .distinct()
        .forEach(accountingService::requireUnlockedAccountingDate);
  }

  private String normalizeStatus(String status) {
    String cleanStatus = clean(status).toLowerCase();
    if ("approved".equals(cleanStatus) || "pending".equals(cleanStatus) || "blocked".equals(cleanStatus)) {
      return cleanStatus;
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher approval status must be approved, pending or blocked.");
  }

  private String clean(String value) {
    if (value == null) {
      return "";
    }

    String trimmed = value.trim();
    return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
  }
}
