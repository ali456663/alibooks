package se.cloudshop.accounting;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;

@RestController
public class OwnerTransactionController {

  private final AuthHeader authHeader;
  private final OwnerTransactionRepository ownerTransactionRepository;
  private final AccountingService accountingService;
  private final AuditService auditService;

  public OwnerTransactionController(
      AuthHeader authHeader,
      OwnerTransactionRepository ownerTransactionRepository,
      AccountingService accountingService,
      AuditService auditService
  ) {
    this.authHeader = authHeader;
    this.ownerTransactionRepository = ownerTransactionRepository;
    this.accountingService = accountingService;
    this.auditService = auditService;
  }

  @GetMapping("/owner-transactions")
  public List<OwnerTransaction> getOwnerTransactions(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return ownerTransactionRepository.findAllByOrderByDateDescIdDesc();
  }

  @PostMapping("/owner-transactions")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public OwnerTransaction createOwnerTransaction(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateOwnerTransactionRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    String type = normalizeType(request == null ? "" : request.type());
    if (request == null || request.date() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction date is required.");
    }
    if (request.amount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction amount must be greater than zero.");
    }

    accountingService.requireUnlockedAccountingDate(request.date());
    OwnerTransactionAccounts accounts = accountsFor(type);
    OwnerTransaction transaction = ownerTransactionRepository.save(new OwnerTransaction(
        type,
        request.date(),
        request.amount(),
        clean(request.description()).isBlank() ? defaultDescription(type) : clean(request.description()),
        clean(request.reference()),
        accounts.debitAccount(),
        accounts.creditAccount()
    ));

    auditService.record("owner_equity", "owner_transaction", transaction.getId(), "created", transaction.getReference(), "Owner transaction created", transaction.getAmount(), authorizationHeader);
    return transaction;
  }

  @PatchMapping("/owner-transactions/{id}/status")
  @Transactional
  public OwnerTransaction updateOwnerTransactionStatus(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id,
      @RequestBody UpdateOwnerTransactionStatusRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    OwnerTransaction transaction = ownerTransactionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner transaction not found."));

    accountingService.requireUnlockedAccountingDate(transaction.getDate());
    String status = normalizeStatus(request == null ? "" : request.status());
    if ("booked".equals(status) && !hasVoucher(transaction)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the book endpoint to create journal entries for owner transactions.");
    }
    if (hasVoucher(transaction) && !"booked".equals(status)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booked owner transactions cannot be moved back to draft. Create a correction instead.");
    }
    transaction.updateStatus(status);
    OwnerTransaction savedTransaction = ownerTransactionRepository.save(transaction);
    auditService.record("owner_equity", "owner_transaction", savedTransaction.getId(), "status_updated", status, "Owner transaction status updated", savedTransaction.getAmount(), authorizationHeader);
    return savedTransaction;
  }

  @PostMapping("/owner-transactions/{id}/book")
  @Transactional
  public OwnerTransaction bookOwnerTransaction(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    OwnerTransaction transaction = ownerTransactionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner transaction not found."));

    if ("booked".equals(transaction.getStatus()) && hasVoucher(transaction)) {
      return transaction;
    }

    List<JournalEntry> entries = accountingService.createOwnerTransactionEntries(transaction);
    String voucherNumber = entries.isEmpty() ? "" : entries.get(0).getVoucherNumber();
    transaction.markBooked(voucherNumber);
    OwnerTransaction savedTransaction = ownerTransactionRepository.save(transaction);
    auditService.record("owner_equity", "owner_transaction", savedTransaction.getId(), "booked", voucherNumber, "Owner transaction booked", savedTransaction.getAmount(), authorizationHeader);
    return savedTransaction;
  }

  @DeleteMapping("/owner-transactions/{id}")
  @Transactional
  public void deleteOwnerTransaction(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    OwnerTransaction transaction = ownerTransactionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner transaction not found."));

    accountingService.requireUnlockedAccountingDate(transaction.getDate());
    if ("booked".equals(transaction.getStatus()) || hasVoucher(transaction)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booked owner transactions cannot be deleted. Create a correction instead.");
    }
    ownerTransactionRepository.delete(transaction);
    auditService.record("owner_equity", "owner_transaction", id, "deleted", transaction.getReference(), "Owner transaction deleted", transaction.getAmount(), authorizationHeader);
  }

  private String normalizeType(String type) {
    String normalized = clean(type);
    if (List.of("deposit", "withdrawal", "taxPayment").contains(normalized)) {
      return normalized;
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction type must be deposit, withdrawal or taxPayment.");
  }

  private String normalizeStatus(String status) {
    String normalized = clean(status);
    if (List.of("draft", "prepared", "booked").contains(normalized)) {
      return normalized;
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner transaction status must be draft, prepared or booked.");
  }

  private OwnerTransactionAccounts accountsFor(String type) {
    if ("withdrawal".equals(type)) {
      return new OwnerTransactionAccounts("2013", "1930");
    }
    if ("taxPayment".equals(type)) {
      return new OwnerTransactionAccounts("2012", "1930");
    }

    return new OwnerTransactionAccounts("1930", "2018");
  }

  private String defaultDescription(String type) {
    if ("withdrawal".equals(type)) {
      return "Eget uttag";
    }
    if ("taxPayment".equals(type)) {
      return "Betalning till skattekonto";
    }

    return "Egen insattning";
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean hasVoucher(OwnerTransaction transaction) {
    return transaction.getVoucherNumber() != null && !transaction.getVoucherNumber().isBlank();
  }

  private record OwnerTransactionAccounts(String debitAccount, String creditAccount) {
  }
}
