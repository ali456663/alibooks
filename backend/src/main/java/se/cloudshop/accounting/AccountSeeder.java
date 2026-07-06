package se.cloudshop.accounting;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AccountSeeder implements CommandLineRunner {

  private final AccountRepository accountRepository;

  public AccountSeeder(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  public void run(String... args) {
    upsert("1930", "Foretagskonto", "BOTH");
    upsert("1580", "Fordran hos Stripe", "BOTH");
    upsert("1510", "Kundfordringar", "BOTH");
    upsert("2611", "Utgaende moms", "BOTH");
    upsert("2641", "Ingaende moms", "BOTH");
    upsert("3041", "Forsaljning tjanster 25 procent", "BOTH");
    upsert("4010", "Inkop", "BOTH");
    upsert("5410", "Forbrukningsinventarier", "BOTH");
    upsert("5420", "Programvaror", "BOTH");
    upsert("5800", "Resekostnader", "BOTH");
    upsert("6570", "Bankkostnader", "BOTH");
    upsert("7010", "Lon till anstallda", "BOTH");
    upsert("7210", "Lon till tjansteman", "BOTH");
    upsert("7510", "Arbetsgivaravgifter", "BOTH");

    upsert("2010", "Eget kapital", "SOLE_TRADER");
    upsert("2012", "Avrakning skatter och avgifter", "SOLE_TRADER");
    upsert("2013", "Egna uttag", "SOLE_TRADER");
    upsert("2018", "Egna insattningar", "SOLE_TRADER");

    upsert("2081", "Aktiekapital", "LIMITED_COMPANY");
    upsert("2091", "Balanserad vinst eller forlust", "LIMITED_COMPANY");
    upsert("2099", "Arets resultat", "LIMITED_COMPANY");
    upsert("2440", "Leverantorsskulder", "LIMITED_COMPANY");
    upsert("2510", "Skatteskulder", "LIMITED_COMPANY");
    upsert("2710", "Personalskatt", "LIMITED_COMPANY");
    upsert("2731", "Avrakning sociala avgifter", "LIMITED_COMPANY");
  }

  private void upsert(String number, String name, String companyType) {
    Account account = accountRepository.findByNumber(number)
        .orElseGet(() -> new Account(number, name, companyType));
    account.update(name, companyType);
    accountRepository.save(account);
  }
}
