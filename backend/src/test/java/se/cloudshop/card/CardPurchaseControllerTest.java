package se.cloudshop.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;
import se.cloudshop.expense.Expense;
import se.cloudshop.expense.ExpenseRepository;

class CardPurchaseControllerTest {

  private final AuthHeader authHeader = new AuthHeader(new JwtService("test_secret"));
  private final CardPurchaseRepository cardPurchaseRepository = mock(CardPurchaseRepository.class);
  private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
  private final AccountingService accountingService = mock(AccountingService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final CardPurchaseController cardPurchaseController = new CardPurchaseController(
      authHeader,
      cardPurchaseRepository,
      expenseRepository,
      accountingService,
      auditService
  );

  @Test
  void createCardPurchaseRejectsVatGreaterThanTotal() {
    CreateCardPurchaseRequest request = new CreateCardPurchaseRequest(
        LocalDate.of(2026, 8, 17),
        "Adobe",
        "Ali",
        "1234",
        "card-1",
        100,
        125,
        "5420",
        "2890"
    );

    assertThatThrownBy(() -> cardPurchaseController.createCardPurchase(authorizationHeader(), request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("VAT must be between zero and total amount");
  }

  @Test
  void bookCardPurchaseCreatesExpensePaidFromCardClearing() {
    String authorizationHeader = authorizationHeader();
    CardPurchase purchase = new CardPurchase(
        LocalDate.of(2026, 8, 17),
        "Adobe",
        "Ali",
        "123456789",
        "card-1",
        125,
        25,
        "5420",
        "2890"
    );
    when(cardPurchaseRepository.findById(1L)).thenReturn(Optional.of(purchase));
    when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(cardPurchaseRepository.save(any(CardPurchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CardPurchase savedPurchase = cardPurchaseController.bookCardPurchaseAsExpense(authorizationHeader, 1L);

    assertThat(savedPurchase.getStatus()).isEqualTo("booked");
    verify(accountingService).requireUnlockedAccountingDate(LocalDate.of(2026, 8, 17));
    verify(accountingService).createExpenseEntries(any(Expense.class));
    verify(auditService).record(
        eq("card_purchase"),
        eq("card_purchase"),
        any(),
        eq("booked"),
        eq("card-1"),
        org.mockito.ArgumentMatchers.contains("Card purchase booked as expense"),
        eq(125),
        eq(authorizationHeader)
    );
  }

  private String authorizationHeader() {
    return "Bearer " + new JwtService("test_secret").createToken("test@example.com");
  }
}
