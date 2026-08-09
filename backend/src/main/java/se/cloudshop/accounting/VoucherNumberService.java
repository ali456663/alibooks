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
    int latestVoucherNumber = journalEntryRepository.findAll().stream()
        .map(JournalEntry::getVoucherNumber)
        .filter(voucherNumber -> voucherNumber != null && voucherNumber.startsWith(prefix + "-"))
        .map(voucherNumber -> voucherNumber.substring((prefix + "-").length()))
        .mapToInt(this::parseVoucherSequence)
        .max()
        .orElse(0);

    return prefix + "-" + (latestVoucherNumber + 1);
  }

  private String normalizeSeries(String series) {
    if (series == null || series.isBlank()) {
      return "V";
    }

    return series.trim().toUpperCase();
  }

  private int parseVoucherSequence(String sequence) {
    if (sequence == null || sequence.isBlank()) {
      return 0;
    }

    try {
      return Integer.parseInt(sequence.trim());
    } catch (NumberFormatException exception) {
      return 0;
    }
  }
}
