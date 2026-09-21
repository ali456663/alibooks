package se.cloudshop.card;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.expense.Expense;
import se.cloudshop.expense.ExpenseRepository;

@RestController
public class CardPurchaseController {

  private final AuthHeader authHeader;
  private final CardPurchaseRepository cardPurchaseRepository;
  private final ExpenseRepository expenseRepository;
  private final AccountingService accountingService;
  private final AuditService auditService;

  public CardPurchaseController(
      AuthHeader authHeader,
      CardPurchaseRepository cardPurchaseRepository,
      ExpenseRepository expenseRepository,
      AccountingService accountingService,
      AuditService auditService
  ) {
    this.authHeader = authHeader;
    this.cardPurchaseRepository = cardPurchaseRepository;
    this.expenseRepository = expenseRepository;
    this.accountingService = accountingService;
    this.auditService = auditService;
  }

  @GetMapping("/card-purchases")
  public List<CardPurchase> getCardPurchases(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return cardPurchaseRepository.findAllByOrderByPurchaseDateDescIdDesc();
  }

  @PostMapping("/card-purchases")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public CardPurchase createCardPurchase(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateCardPurchaseRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request == null || request.purchaseDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase date is required.");
    }
    if (clean(request.merchantName()).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchant name is required.");
    }
    if (request.totalAmount() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total amount must be greater than zero.");
    }
    if (request.vatAmount() < 0 || request.vatAmount() > request.totalAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VAT must be between zero and total amount.");
    }

    accountingService.requireUnlockedAccountingDate(request.purchaseDate());
    CardPurchase purchase = cardPurchaseRepository.save(new CardPurchase(
        request.purchaseDate(),
        clean(request.merchantName()),
        clean(request.cardHolder()),
        cleanCardLast4(request.cardLast4()),
        clean(request.reference()),
        request.totalAmount(),
        request.vatAmount(),
        defaultValue(request.category(), "5420"),
        defaultValue(request.clearingAccount(), "2890")
    ));

    auditService.record("card_purchase", "card_purchase", purchase.getId(), "created", purchase.getReference(), "Card purchase received for review", purchase.getTotalAmount(), authorizationHeader);
    return purchase;
  }

  @PostMapping("/card-purchases/{id}/book-expense")
  @Transactional
  public CardPurchase bookCardPurchaseAsExpense(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    CardPurchase purchase = cardPurchaseRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card purchase not found."));

    if ("booked".equals(purchase.getStatus()) && purchase.getBookedExpenseId() != null) {
      return purchase;
    }

    int totalAmount = wholeKrona(purchase.getTotalAmountMinor(), purchase.getTotalAmount(), "kortköpets totalbelopp");
    int netAmount = wholeKrona(purchase.getNetAmountMinor(), purchase.getNetAmount(), "kortköpets nettobelopp");
    int vatAmount = wholeKrona(purchase.getVatAmountMinor(), purchase.getVatAmount(), "kortköpets momsbelopp");
    accountingService.requireUnlockedAccountingDate(purchase.getPurchaseDate());
    Expense expense = expenseRepository.save(new Expense(
        purchase.getPurchaseDate(),
        "Kortkop: " + purchase.getMerchantName(),
        netAmount,
        vatAmount,
        purchase.getCategory(),
        defaultValue(purchase.getClearingAccount(), "2890")
    ));
    accountingService.createExpenseEntries(expense);
    purchase.markBooked(expense.getId());
    CardPurchase savedPurchase = cardPurchaseRepository.save(purchase);
    auditService.record("card_purchase", "card_purchase", savedPurchase.getId(), "booked", savedPurchase.getReference(), "Card purchase booked as expense " + expense.getId(),
        totalAmount, authorizationHeader);
    return savedPurchase;
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

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String defaultValue(String value, String fallback) {
    String cleanValue = clean(value);
    return cleanValue.isBlank() ? fallback : cleanValue;
  }

  private String cleanCardLast4(String value) {
    String digits = clean(value).replaceAll("\\D", "");
    if (digits.length() <= 4) {
      return digits;
    }

    return digits.substring(digits.length() - 4);
  }
}
