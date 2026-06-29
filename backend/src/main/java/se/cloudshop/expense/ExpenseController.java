package se.cloudshop.expense;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import se.cloudshop.auth.AuthHeader;

@RestController
public class ExpenseController {

  private static final Path RECEIPT_DIRECTORY = Path.of("uploads", "receipts");

  private final AuthHeader authHeader;
  private final ExpenseRepository expenseRepository;
  private final AccountingService accountingService;

  public ExpenseController(
      AuthHeader authHeader,
      ExpenseRepository expenseRepository,
      AccountingService accountingService
  ) {
    this.authHeader = authHeader;
    this.expenseRepository = expenseRepository;
    this.accountingService = accountingService;
  }

  @GetMapping("/expenses")
  public List<Expense> getExpenses(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return expenseRepository.findAll();
  }

  @PostMapping("/expenses")
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

    Expense expense = expenseRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found."));

    try {
      Path receiptDirectory = RECEIPT_DIRECTORY.toAbsolutePath().normalize();
      Files.createDirectories(receiptDirectory);
      String originalFilename = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
      String safeFilename = originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
      Path target = receiptDirectory.resolve(id + "-" + UUID.randomUUID() + "-" + safeFilename).normalize();
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

      expense.setReceipt(originalFilename, file.getContentType(), target.toString());
      return expenseRepository.save(expense);
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save receipt.");
    }
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

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + expense.getReceiptFileName() + "\"")
        .contentType(mediaType)
        .body(resource);
  }
}
