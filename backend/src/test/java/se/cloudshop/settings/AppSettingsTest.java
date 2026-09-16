package se.cloudshop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppSettingsTest {

  @Test
  void defaultsDoNotContainPersonalOrStalePaymentDetails() {
    AppSettings defaults = AppSettings.defaults();

    assertThat(defaults.getContactEmail()).isBlank();
    assertThat(defaults.getPlusGiro()).isBlank();
    assertThat(defaults.getDefaultOcr()).isBlank();
    assertThat(defaults.getPaymentRecipient()).isBlank();
  }
}
