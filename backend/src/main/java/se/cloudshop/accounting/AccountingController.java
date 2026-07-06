package se.cloudshop.accounting;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class AccountingController {

  private final AccountingService accountingService;
  private final AuthHeader authHeader;

  public AccountingController(AccountingService accountingService, AuthHeader authHeader) {
    this.accountingService = accountingService;
    this.authHeader = authHeader;
  }

  @GetMapping("/journal-entries")
  public List<JournalEntry> getJournalEntries(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.findAllEntries();
  }

  @GetMapping("/stripe-payouts")
  public List<StripePayout> getStripePayouts(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.findStripePayouts();
  }

  @PostMapping("/journal-entries/manual")
  public List<JournalEntry> createManualJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateManualJournalEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createManualEntry(request);
  }

  @PostMapping("/journal-entries/manual-multi")
  public List<JournalEntry> createManualMultiLineJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateManualMultiLineJournalEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createManualMultiLineEntry(request);
  }

  @PostMapping("/journal-entries/stripe-website-sale")
  public List<JournalEntry> createStripeWebsiteSaleJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateStripeWebsiteSaleRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createStripeWebsiteSaleEntry(request);
  }

  @PostMapping("/journal-entries/stripe-payout")
  public List<JournalEntry> createStripePayoutJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateStripePayoutRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createStripePayoutEntry(request);
  }

  @PostMapping("/journal-entries/{voucherNumber}/correction")
  public List<JournalEntry> createCorrectionJournalEntry(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String voucherNumber,
      @RequestBody(required = false) CreateCorrectionJournalEntryRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createCorrectionEntry(voucherNumber, request);
  }

  @GetMapping("/vat-report")
  public VatReport getVatReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createVatReport();
  }

  @GetMapping("/profit-and-loss")
  public ProfitAndLossReport getProfitAndLossReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createProfitAndLossReport();
  }

  @GetMapping("/balance-report")
  public BalanceReport getBalanceReport(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return accountingService.createBalanceReport();
  }
}
