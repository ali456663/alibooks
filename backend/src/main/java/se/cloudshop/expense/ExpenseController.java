package se.cloudshop.expense;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
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
  private final se.cloudshop.bank.BankImportBookingService bankImport;

  public ExpenseController(
      AuthHeader authHeader,
      ExpenseRepository expenseRepository,
      AccountingService accountingService,
      AuditService auditService,
      se.cloudshop.bank.BankImportBookingService bankImport
  ) {
    this.authHeader = authHeader;
    this.expenseRepository = expenseRepository;
    this.accountingService = accountingService;
    this.auditService = auditService;
    this.bankImport = bankImport;
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
    if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense is required.");
    if (request.expenseDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense date is required.");
    }
    if (request.bankRow() != null) {
      if (!"1930".equals(request.paidFrom())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank import must use account 1930.");
      bankImport.reserve(request.bankRow(), request.expenseDate(), -((long) request.netAmount() + request.vatAmount()));
    }

    if (request.description() == null || request.description().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
    }

    if (request.netAmount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Net amount must be greater than zero.");
    }
    if (request.vatAmount() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT amount must not be negative.");
    }
    if ((long) request.netAmount() + request.vatAmount() > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense total exceeds the supported limit.");
    }

    LocalDate expenseDate = request.expenseDate();
    accountingService.requireUnlockedAccountingDate(expenseDate);

    Expense expense = expenseRepository.save(new Expense(
        expenseDate,
        request.description(),
        request.netAmount(),
        request.vatAmount(),
        request.category() == null || request.category().isBlank() ? "5420" : request.category(),
        request.paidFrom() == null || request.paidFrom().isBlank() ? "1930" : request.paidFrom()
    ));

    var bankEntry = accountingService.createExpenseEntries(expense);
    auditService.record("expense", "expense", expense.getId(), "created", expense.getDescription(), "Expense created and booked",
        wholeKrona(expense.getTotalAmountMinor(), expense.getTotalAmount(), "kostnadens totalbelopp"), authorizationHeader);
    if (request.bankRow() != null) bankImport.record(request.bankRow(), "expense", "Expense " + expense.getId(), bankEntry, authorizationHeader);
    return expense;
  }

  @PostMapping("/bank-import/expenses")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Expense createBankExpense(@RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody CreateExpenseRequest request) {
    authHeader.requireValidToken(authorization);
    if (request == null || request.bankRow() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank row is required.");
    return createExpense(authorization, request);
  }

  @PostMapping("/expenses/{id}/receipt")
  @Transactional
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

    byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read receipt file.");
    }
    String detectedContentType = detectReceiptContentType(fileBytes);
    if (!contentType.equals(detectedContentType) || !hasMatchingExtension(originalFilename, detectedContentType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt content does not match its file type.");
    }

    Expense expense = expenseRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found."));

    if (expense.hasReceipt()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt already exists. Create a new correction/evidence record instead of replacing archived evidence.");
    }

    Path target = null;
    try {
      String receiptSha256 = sha256(fileBytes);
      Path normalizedReceiptDirectory = RECEIPT_DIRECTORY.toAbsolutePath().normalize();
      Files.createDirectories(normalizedReceiptDirectory);
      String safeFilename = originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
      target = normalizedReceiptDirectory.resolve(id + "-" + UUID.randomUUID() + "-" + safeFilename).normalize();
      Files.write(target, fileBytes, java.nio.file.StandardOpenOption.CREATE_NEW);

      expense.setReceipt(originalFilename, detectedContentType, target.toString(), receiptSha256, Instant.now());
      Expense savedExpense = expenseRepository.save(expense);
      auditService.record("expense", "expense", savedExpense.getId(), "receipt_uploaded", savedExpense.getReceiptFileName(), "Receipt uploaded. SHA-256: " + receiptSha256,
          wholeKrona(savedExpense.getTotalAmountMinor(), savedExpense.getTotalAmount(), "kostnadens totalbelopp"), authorizationHeader);
      return savedExpense;
    } catch (IOException exception) {
      deleteReceiptFileAfterFailedUpload(target);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save receipt.");
    } catch (RuntimeException exception) {
      deleteReceiptFileAfterFailedUpload(target);
      throw exception;
    }
  }

  private void deleteReceiptFileAfterFailedUpload(Path target) {
    if (target == null) return;
    try {
      Files.deleteIfExists(target);
    } catch (IOException ignored) {
      // Preserve the original failure. The missing cleanup is visible through the receipt repair check.
    }
  }

  private int wholeKrona(Long amountMinor, int legacyAmount, String field) {
    long valueMinor;
    try {
      valueMinor = amountMinor == null ? Math.multiplyExact((long) legacyAmount, 100L) : amountMinor;
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          field + " ligger utanför det belopp som bokföringen kan representera.");
    }
    if (valueMinor % 100L != 0L) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          field + " innehåller ören som bokföringen inte kan representera.");
    }
    long wholeKrona = valueMinor / 100L;
    if (wholeKrona < Integer.MIN_VALUE || wholeKrona > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          field + " ligger utanför det belopp som bokföringen kan representera.");
    }
    return (int) wholeKrona;
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
            wholeKrona(savedExpense.getTotalAmountMinor(), savedExpense.getTotalAmount(), "kostnadens totalbelopp"),
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

    String storedContentType = clean(expense.getReceiptContentType()).toLowerCase();
    MediaType mediaType = ALLOWED_RECEIPT_CONTENT_TYPES.contains(storedContentType)
        ? MediaType.parseMediaType(storedContentType)
        : MediaType.APPLICATION_OCTET_STREAM;

    ContentDisposition contentDisposition = ContentDisposition.inline()
        .filename(receiptDownloadFilename(expense.getReceiptFileName()), StandardCharsets.UTF_8)
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
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

  private String detectReceiptContentType(byte[] bytes) {
    if (startsWith(bytes, new byte[] {'%', 'P', 'D', 'F', '-'})) return "application/pdf";
    if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
    if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
    if (bytes.length >= 12
        && startsWith(bytes, new byte[] {'R', 'I', 'F', 'F'})
        && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), new byte[] {'W', 'E', 'B', 'P'})) {
      return "image/webp";
    }
    return "";
  }

  private boolean startsWith(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) {
      if (value[index] != prefix[index]) return false;
    }
    return true;
  }

  private boolean hasMatchingExtension(String filename, String contentType) {
    String cleanFilename = clean(filename).toLowerCase();
    return switch (contentType) {
      case "application/pdf" -> cleanFilename.endsWith(".pdf");
      case "image/jpeg" -> cleanFilename.endsWith(".jpg") || cleanFilename.endsWith(".jpeg");
      case "image/png" -> cleanFilename.endsWith(".png");
      case "image/webp" -> cleanFilename.endsWith(".webp");
      default -> false;
    };
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
