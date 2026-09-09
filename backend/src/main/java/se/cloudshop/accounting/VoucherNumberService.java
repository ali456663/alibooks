package se.cloudshop.accounting;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class VoucherNumberService {

  private final JournalEntryRepository journalEntryRepository;
  private final JdbcTemplate jdbcTemplate;

  public VoucherNumberService(JournalEntryRepository journalEntryRepository, JdbcTemplate jdbcTemplate) {
    this.journalEntryRepository = journalEntryRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public String nextVoucherNumber(String series) {
    String prefix = normalizeSeries(series);
    // Keep the series reserved until the caller commits all voucher rows.
    jdbcTemplate.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?, hashtext(?))",
        Integer.class, 4279369, prefix);
    int latestVoucherNumber = journalEntryRepository.findAll().stream()
        .map(JournalEntry::getVoucherNumber)
        .filter(voucherNumber -> voucherNumber != null && voucherNumber.startsWith(prefix + "-"))
        .map(voucherNumber -> voucherNumber.substring((prefix + "-").length()))
        .mapToInt(this::parseVoucherSequence)
        .max()
        .orElse(0);

    return prefix + "-" + Math.incrementExact(latestVoucherNumber);
  }

  private String normalizeSeries(String series) {
    if (series == null || series.isBlank()) {
      return "V";
    }

    return series.trim().toUpperCase(Locale.ROOT);
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
