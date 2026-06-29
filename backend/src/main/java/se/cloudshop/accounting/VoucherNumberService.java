package se.cloudshop.accounting;

import org.springframework.stereotype.Service;

@Service
public class VoucherNumberService {

  private final JournalEntryRepository journalEntryRepository;

  public VoucherNumberService(JournalEntryRepository journalEntryRepository) {
    this.journalEntryRepository = journalEntryRepository;
  }

  public String nextVoucherNumber(String series) {
    String prefix = normalizeSeries(series);
    long existingVoucherCount = journalEntryRepository.findAll().stream()
        .map(JournalEntry::getVoucherNumber)
        .filter(voucherNumber -> voucherNumber != null && voucherNumber.startsWith(prefix + "-"))
        .distinct()
        .count();

    return prefix + "-" + (existingVoucherCount + 1);
  }

  private String normalizeSeries(String series) {
    if (series == null || series.isBlank()) {
      return "V";
    }

    return series.trim().toUpperCase();
  }
}
