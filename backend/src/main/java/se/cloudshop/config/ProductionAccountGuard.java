package se.cloudshop.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import se.cloudshop.auth.UserRepository;

@Component
@ConditionalOnProperty(prefix = "app", name = "environment", havingValue = "production")
public class ProductionAccountGuard implements SmartInitializingSingleton {

  private final UserRepository users;

  public ProductionAccountGuard(UserRepository users) {
    this.users = users;
  }

  @Override
  public void afterSingletonsInstantiated() {
    long count = users.count();
    if (count > 1) {
      throw new IllegalStateException(
          "Production is limited to one owner account until user and company isolation is implemented");
    }
  }
}
