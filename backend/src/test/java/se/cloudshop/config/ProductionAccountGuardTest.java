package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import se.cloudshop.auth.UserRepository;

class ProductionAccountGuardTest {

  private final UserRepository users = mock(UserRepository.class);
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfiguration.class)
      .withBean(UserRepository.class, () -> users);

  @Test
  void allowsInitialBootstrapWithoutAnAccount() {
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Test
  void allowsOneOwnerAccount() {
    when(users.count()).thenReturn(1L);
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Test
  void blocksExistingMultipleAccounts() {
    when(users.count()).thenReturn(2L);
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure())
            .hasMessageContaining("Production is limited to one owner account until user and company isolation is implemented"));
  }

  @Test
  void databaseFailureDoesNotPassAsAnEmptyWorkspace() {
    when(users.count()).thenThrow(new IllegalStateException("database unavailable"));
    runner.withPropertyValues("app.environment=production")
        .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("database unavailable"));
  }

  @Test
  void localModeDoesNotCheckAccountCount() {
    when(users.count()).thenThrow(new IllegalStateException("must not query"));
    runner.withPropertyValues("app.environment=local")
        .run(context -> assertThat(context.getStartupFailure()).isNull());
  }

  @Configuration(proxyBeanMethods = false)
  @Import(ProductionAccountGuard.class)
  static class TestConfiguration {}
}
