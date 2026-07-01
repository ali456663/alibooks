package se.cloudshop.bank;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.auth.AuthHeader;

@RestController
public class BankReconciliationController {

  private final AuthHeader authHeader;
  private final BankReconciliationEntryRepository bankReconciliationEntryRepository;

  public BankReconciliationController(
      AuthHeader authHeader,
      BankReconciliationEntryRepository bankReconciliationEntryRepository
  ) {
    this.authHeader = authHeader;
    this.bankReconciliationEntryRepository = bankReconciliationEntryRepository;
  }

  @GetMapping("/bank-reconciliations")
  public List<BankReconciliationEntry> getBankReconciliations(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return bankReconciliationEntryRepository.findAllByOrderByBookedAtDesc();
  }

  @PostMapping("/bank-reconciliations")
  @ResponseStatus(HttpStatus.CREATED)
  public BankReconciliationEntry createBankReconciliation(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateBankReconciliationEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank reconciliation entry is required.");
    }

    if (request.amount() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required.");
    }

    return bankReconciliationEntryRepository.save(new BankReconciliationEntry(request));
  }

  @DeleteMapping("/bank-reconciliations")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearBankReconciliations(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    bankReconciliationEntryRepository.deleteAll();
  }

  @DeleteMapping("/bank-reconciliations/skipped/{bankRowId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void removeSkippedBankReconciliation(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String bankRowId
  ) {
    authHeader.requireValidToken(authorizationHeader);
    bankReconciliationEntryRepository.deleteByBankRowIdAndStatus(bankRowId, "skipped");
  }
}
