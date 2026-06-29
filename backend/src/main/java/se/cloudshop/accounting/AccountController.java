package se.cloudshop.accounting;

import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.settings.SettingsService;

@RestController
public class AccountController {

  private final AccountRepository accountRepository;
  private final AuthHeader authHeader;
  private final SettingsService settingsService;

  public AccountController(AccountRepository accountRepository, AuthHeader authHeader, SettingsService settingsService) {
    this.accountRepository = accountRepository;
    this.authHeader = authHeader;
    this.settingsService = settingsService;
  }

  @GetMapping("/accounts")
  public List<Account> getAccounts(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    String companyType = settingsService.getSettings().getCompanyType();
    if (companyType == null || companyType.isBlank()) {
      companyType = "SOLE_TRADER";
    }
    String selectedCompanyType = companyType;

    return accountRepository.findAll()
        .stream()
        .filter(account -> "BOTH".equals(account.getCompanyType()) || selectedCompanyType.equals(account.getCompanyType()))
        .sorted(Comparator.comparing(Account::getNumber))
        .toList();
  }
}
