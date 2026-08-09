package se.cloudshop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudShopApplicationTests {

  @Test
  void contextLoads() {
    new ApplicationContextRunner()
        .withBean(SpringApplication.class, () -> new SpringApplication(CloudShopApplication.class))
        .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasNotFailed());
  }
}
