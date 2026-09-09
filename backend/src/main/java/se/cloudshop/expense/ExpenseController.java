package se.cloudshop.expense;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;

@RestController
public class ExpenseController {

  private static final Path RECEIPT_DIRECTORY = Path.of("uploads", "receipts");
  private static final List<String> ALLOWED_RECEIPT_CONTENT_TYPES = List.of(
      "application/pdf",
      "image/jpeg",
      "image/png",
      "image/webp"
  );

  private final AuthHeader authHeader;
  private final ExpenseRepository expenseRepository;
  private final AccountingService accountingService;
  private final AuditService auditService;

  public ExpenseController(
      AuthHeader authHeader,
      ExpenseRepository expenseRepository,
      AccountingService accountingService,
      AuditService auditService
  ) {
    this.authHeader = authHeader;
    this.expenseRepository = expenseRepository;
    this.accountingService = accountingService;
    this.auditService = auditService;
  }

  @GetMapping("/expenses")
  public List<Expense> getExpenses(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return expenseRepository.findAll();
  }

  @PostMapping("/expenses")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Expense createExpense(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateExpenseRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request.description() == null || request.description().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
    }

    if (request.netAmount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Net amount must be greater than zero.");
    }

    LocalDate expenseDate = request.expenseDate() == null ? LocalDate.now() : request.expenseDate();
    accountingService.requireUnlockedAccountingDate(expenseDate);

    Expense expense = expenseRepository.save(new Expense(
        expenseDate,
        request.description(),
        request.netAmount(),
        Math.max(request.vatAmount(), 0),
        request.category() == null || request.category().isBlank() ? "5420" : request.category(),
        request.paidFrom() == null || request.paidFrom().isBlank() ? "1930" : request.paidFrom()
    ));

    accountingService.createExpenseEntries(expense);
    auditService.record("expense", "expense", expense.getId(), "created", expense.getDescription(), "Expense created and booked", expense.getTotalAmount(), authorizationHeader);
    return expense;
  }

  @PostMapping("/expenses/{id}/receipt")
  public Expense uploadReceipt(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file is required.");
    }

    if (file.getSize() > 10 * 1024 * 1024) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file can be max 10 MB.");
    }

    String contentType = clean(file.getContentType()).toLowerCase();
    String originalFilename = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
    if (!isAllowedReceiptFile(contentType, originalFilename)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file must be PDF, JPG, PNG or WebP.");
    }

    Expense expense = expenseRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found."));

    if (expense.hasReceipt()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt already exists. Create a new correction/evidence record instead of replacing archived evidence.");
    }

    try {
      byte[] fileBytes = file.getBytes();
      String receiptSha256 = sha256(fileBytes);
      Path receiptDirectory = RECEIPT_DIRECTORY.toAbsolutePath().normalize();
      Files.createDirectories(receiptDirectory);
      String safeFilename = originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
      Path target = receiptDirectory.resolve(id + "-" + UUID.randomUUID() + "-" + safeFilename).normalize();
      Files.write(target, fileBytes);

      expense.setReceipt(originalFilename, file.getContentType(), target.toString(), receiptSha256, Instant.now());
      Expense savedExpense = expenseRepository.save(expense);
      auditService.record("expense", "expense", savedExpense.getId(), "receipt_uploaded", savedExpense.getReceiptFileName(), "Receipt uploaded. SHA-256: " + receiptSha256, savedExpense.getTotalAmount(), authorizationHeader);
      return savedExpense;
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save receipt.");
    }
  }

  @PostMapping("/expenses/receipt-hashes/repair")
  public ReceiptHashRepairResult repairReceiptHashes(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    int scannedCount = 0;
    int repairedCount = 0;
    int alreadyHasHashCount = 0;
    int noReceiptCount = 0;
    int missingFileCount = 0;
    int lockedSkippedCount = 0;
    int failedCount = 0;

    for (Expense expense : expenseRepository.findAll()) {
      scannedCount++;

      if (!expense.hasReceipt()) {
        noReceiptCount++;
        continue;
      }

      if (expense.getReceiptSha256() != null && !expense.getReceiptSha256().isBlank()) {
        alreadyHasHashCount++;
        continue;
      }

      try {
        accountingService.requireUnlockedAccountingDate(expense.getExpenseDate());
      } catch (ResponseStatusException exception) {
        lockedSkippedCount++;
        continue;
      }

      try {
        Path receiptPath = Path.of(expense.getReceiptStoragePath()).toAbsolutePath().normalize();
        if (!Files.exists(receiptPath)) {
          missingFileCount++;
          continue;
        }

        String receiptSha256 = sha256(Files.readAllBytes(receiptPath));
        expense.setReceipt(
            expense.getReceiptFileName(),
            expense.getReceiptContentType(),
            expense.getReceiptStoragePath(),
            receiptSha256,
            expense.getReceiptUploadedAt()
        );
        Expense savedExpense = expenseRepository.save(expense);
        repairedCount++;
        auditService.record(
            "expense",
            "expense",
            savedExpense.getId(),
            "receipt_hash_repaired",
            savedExpense.getReceiptFileName(),
            "Receipt SHA-256 repaired from archived file. SHA-256: " + receiptSha256,
            savedExpense.getTotalAmount(),
            authorizationHeader
        );
      } catch (IOException | InvalidPathException exception) {
        failedCount++;
      }
    }

    return new ReceiptHashRepairResult(
        scannedCount,
        repairedCount,
        alreadyHasHashCount,
        noReceiptCount,
        missingFileCount,
        lockedSkippedCount,
        failedCount
    );
  }

  @GetMapping("/expenses/{id}/receipt")
  public ResponseEntity<Resource> downloadReceipt(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);

    Expense expense = expenseRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found."));

    if (!expense.hasReceipt()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found.");
    }

    FileSystemResource resource = new FileSystemResource(expense.getReceiptStoragePath());

    if (!resource.exists()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt file is missing.");
    }

    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

    if (expense.getReceiptContentType() != null && !expense.getReceiptContentType().isBlank()) {
      mediaType = MediaType.parseMediaType(expense.getReceiptContentType());
    }

    ContentDisposition contentDisposition = ContentDisposition.inline()
        .filename(receiptDownloadFilename(expense.getReceiptFileName()), StandardCharsets.UTF_8)
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
        .contentType(mediaType)
        .body(resource);
  }

  private boolean isAllowedReceiptFile(String contentType, String originalFilename) {
    String filename = clean(originalFilename).toLowerCase();
    boolean allowedContentType = ALLOWED_RECEIPT_CONTENT_TYPES.contains(contentType);
    boolean allowedExtension = filename.endsWith(".pdf")
        || filename.endsWith(".jpg")
        || filename.endsWith(".jpeg")
        || filename.endsWith(".png")
        || filename.endsWith(".webp");

    return allowedContentType && allowedExtension;
  }

  private String receiptDownloadFilename(String filename) {
    String cleanFilename = clean(filename)
        .replaceAll("[\\r\\n\\\\/]", "_");

    return cleanFilename.isBlank() ? "receipt" : cleanFilename;
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not calculate receipt checksum.");
    }
  }
}
