package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtService jwtService = new JwtService("test_secret");

  @Test
  void createsValidToken() {
    String token = jwtService.createToken("demo@example.com");

    assertThat(jwtService.isValid(token)).isTrue();
  }

  @Test
  void productionMfaClaimIsPresentOnlyAfterSecondFactor() {
    String passwordOnly = jwtService.createToken("demo@example.com");
    String completedMfa = jwtService.createToken("demo@example.com", true);

    assertThat(jwtService.mfaVerified(passwordOnly)).isFalse();
    assertThat(jwtService.mfaVerified(completedMfa)).isTrue();
  }

  @Test
  void extractsSubjectFromValidToken() {
    String token = jwtService.createToken("demo@example.com");

    assertThat(jwtService.subject(token)).contains("demo@example.com");
  }

  @Test
  void exposesOnlySignedNonnegativeAuthenticationVersion() {
    String token = jwtService.createToken("demo@example.com", true, 4);
    assertThat(jwtService.authVersion(token)).hasValue(4);
    String[] parts = token.split("\\.");
    parts[1] = parts[1].substring(0, parts[1].length() - 1) + "A";
    assertThat(jwtService.authVersion(String.join(".", parts))).isEmpty();
  }

  @Test
  void rejectsInvalidToken() {
    assertThat(jwtService.isValid("not.a.real-token")).isFalse();
    assertThat(jwtService.subject("not.a.real-token")).isEmpty();
  }

  @Test
  void rejectsTokenWithModifiedSignature() {
    String token = jwtService.createToken("demo@example.com");
    String[] parts = token.split("\\.");
    char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
    parts[2] = replacement + parts[2].substring(1);

    assertThat(jwtService.isValid(String.join(".", parts))).isFalse();
  }

  @Test
  void exposesConfiguredExpirationMinutes() {
    JwtService shortSessionJwtService = new JwtService("test_secret", 15);

    assertThat(shortSessionJwtService.expirationMinutes()).isEqualTo(15);
  }
}
