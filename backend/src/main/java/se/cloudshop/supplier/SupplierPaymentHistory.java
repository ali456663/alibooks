package se.cloudshop.supplier;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import se.cloudshop.accounting.SettlementSnapshot;

/** Strict reader for the existing supplier payment text format; never guesses legacy totals. */
final class SupplierPaymentHistory {
  private static final Pattern LINE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}) - ([1-9]\\d*) SEK(?: - .*)?$");

  static List<SettlementSnapshot.Payment> read(String history) {
    if (history == null || history.isBlank()) return List.of();
    var payments = new ArrayList<SettlementSnapshot.Payment>();
    for (String line : history.split("\\R", -1)) {
      var match = LINE.matcher(line);
      if (!match.matches()) throw SettlementSnapshot.incomplete();
      try {
        payments.add(new SettlementSnapshot.Payment(LocalDate.parse(match.group(1)), Integer.parseInt(match.group(2))));
      } catch (DateTimeParseException | NumberFormatException exception) {
        throw SettlementSnapshot.incomplete();
      }
    }
    return payments;
  }

  private SupplierPaymentHistory() {}
}
