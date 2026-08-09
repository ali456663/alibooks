package se.cloudshop.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class ExpenseControllerTest {

  @TempDir
  Path tempDir;

  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final ExpenseController expenseController = new ExpenseController(
      authHeader,
      expenseRepository,
      accountingService,
      auditService
  );

  @Test
  void uploadReceiptRejectsReplacingArchivedReceipt() {
    Expense expense = new Expense(LocalDate.of(2026, 7, 1), "Software", 1000, 250, "5420", "1930");
    expense.setReceipt("old.pdf", "application/pdf", "uploads/receipts/old.pdf", "abc123", Instant.now());
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "new.pdf",
        "application/pdf",
        "new receipt".getBytes()
    );

    assertThatThrownBy(() -> expenseController.uploadReceipt("Bearer " + authHeaderToken(), 1L, file))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Receipt already exists");

    verify(expenseRepository, never()).save(any(Expense.class));
  }

  @Test
  void uploadReceiptRejectsUnsupportedFileType() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "receipt.exe",
        "application/octet-stream",
        "not a receipt".getBytes()
    );

    assertThatThrownBy(() -> expenseController.uploadReceipt("Bearer " + authHeaderToken(), 1L, file))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Receipt file must be PDF, JPG, PNG or WebP");

    verify(expenseRepository, never()).findById(1L);
    verify(expenseRepository, never()).save(any(Expense.class));
  }

  @Test
  void repairReceiptHashesBackfillsMissingChecksumFromStoredFile() throws Exception {
    Path receiptPath = tempDir.resolve("old-receipt.pdf");
    Files.writeString(receiptPath, "old receipt", StandardCharsets.UTF_8);
    Expense expense = new Expense(LocalDate.of(2026, 7, 1), "Software", 1000, 250, "5420", "1930");
    expense.setReceipt("old-receipt.pdf", "application/pdf", receiptPath.toString());
    when(expenseRepository.findAll()).thenReturn(List.of(expense));
    when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));
    String authorizationHeader = "Bearer " + authHeaderToken();

    ReceiptHashRepairResult result = expenseController.repairReceiptHashes(authorizationHeader);

    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.repairedCount()).isEqualTo(1);
    assertThat(result.missingFileCount()).isZero();
    assertThat(result.lockedSkippedCount()).isZero();
    assertThat(expense.getReceiptSha256()).hasSize(64).matches("[0-9a-f]+");
    verify(accountingService).requireUnlockedAccountingDate(LocalDate.of(2026, 7, 1));
    verify(auditService).record(
        eq("expense"),
        eq("expense"),
        any(),
        eq("receipt_hash_repaired"),
        eq("old-receipt.pdf"),
        org.mockito.ArgumentMatchers.contains("Receipt SHA-256 repaired"),
        eq(1250),
        eq(authorizationHeader)
    );
  }

  @Test
  void downloadReceiptUsesSafeContentDispositionFilename() throws Exception {
    Path receiptPath = tempDir.resolve("receipt.pdf");
    Files.writeString(receiptPath, "receipt", StandardCharsets.UTF_8);
    Expense expense = new Expense(LocalDate.of(2026, 7, 1), "Software", 1000, 250, "5420", "1930");
    expense.setReceipt("evil\r\nname/receipt.pdf", "application/pdf", receiptPath.toString(), "abc123", Instant.now());
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

    ResponseEntity<Resource> response = expenseController.downloadReceipt("Bearer " + authHeaderToken(), 1L);

    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(contentDisposition).contains("inline");
    assertThat(contentDisposition).doesNotContain("\r").doesNotContain("\n").doesNotContain("/");
    assertThat(contentDisposition).contains("evil__name_receipt.pdf");
  }

  private String authHeaderToken() {
    return new JwtService("test_secret").createToken("test@example.com");
  }
}
