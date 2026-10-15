package se.cloudshop.accounting;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReportAmountExceptionHandler {
  @ExceptionHandler(JournalMoneyIntegrityException.class)
  public ResponseEntity<Map<String, String>> handleLedgerMoney(JournalMoneyIntegrityException exception) {
    return ResponseEntity.unprocessableEntity().header("Cache-Control", "no-store")
        .body(Map.of("code", "LEDGER_MONEY_INTEGRITY", "message", exception.getMessage()));
  }

  @ExceptionHandler(SettlementSnapshot.HistoryIncomplete.class)
  public ResponseEntity<Map<String, String>> handleHistory(SettlementSnapshot.HistoryIncomplete exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(Map.of("code", "SETTLEMENT_HISTORY_INCOMPLETE", "message", exception.getReason()));
  }

  @ExceptionHandler(ReportAmounts.LimitExceeded.class)
  public ResponseEntity<Map<String, String>> handleLimit(ReportAmounts.LimitExceeded exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(Map.of("code", "REPORT_AMOUNT_LIMIT", "message", exception.getReason()));
  }
}
