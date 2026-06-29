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
  void rejectsInvalidToken() {
    assertThat(jwtService.isValid("not.a.real-token")).isFalse();
  }
}

