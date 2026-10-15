package se.cloudshop.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountSeederTest {

  @Test
  void preservesExistingAccountNamesAndCompanyTypesOnRestart() {
    AccountRepository repository = mock(AccountRepository.class);
    Account custom = new Account("1930", "Mitt bankkonto", "LIMITED_COMPANY");
    when(repository.findByNumber("1930")).thenReturn(Optional.of(custom));

    new AccountSeeder(repository).run();

    assertThat(custom.getName()).isEqualTo("Mitt bankkonto");
    assertThat(custom.getCompanyType()).isEqualTo("LIMITED_COMPANY");
    verify(repository, never()).save(custom);
  }

  @Test
  void insertsMissingStandardAccountsWithoutOverwritingExistingOnes() {
    AccountRepository repository = mock(AccountRepository.class);
    Account existing = new Account("1930", "Mitt bankkonto", "BOTH");
    when(repository.findByNumber("1930")).thenReturn(Optional.of(existing));

    new AccountSeeder(repository).run();

    ArgumentCaptor<Account> inserted = ArgumentCaptor.forClass(Account.class);
    verify(repository, atLeastOnce()).save(inserted.capture());
    assertThat(inserted.getAllValues()).noneMatch(account -> "1930".equals(account.getNumber()));
    assertThat(inserted.getAllValues()).anyMatch(account -> "1630".equals(account.getNumber())
        && "Skattekonto".equals(account.getName()));
  }

  @Test
  void doesNotWriteWhenAllStandardAccountsAlreadyExist() {
    AccountRepository repository = mock(AccountRepository.class);
    when(repository.findByNumber(any())).thenAnswer(invocation -> {
      String number = invocation.getArgument(0);
      return Optional.of(new Account(number, "Anpassat namn", "BOTH"));
    });

    new AccountSeeder(repository).run();

    verify(repository, never()).save(any(Account.class));
  }
}
