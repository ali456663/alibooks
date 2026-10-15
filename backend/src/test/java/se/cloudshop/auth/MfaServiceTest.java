package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MfaServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final LoginAttemptService attempts = new LoginAttemptService(5, Duration.ofMinutes(15), java.time.Clock.systemUTC());
  private final org.springframework.security.crypto.password.PasswordEncoder encoder = new org.springframework.security.crypto.password.PasswordEncoder() {
    @Override public String encode(CharSequence rawPassword) { return "test-hash:" + rawPassword; }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) { return encodedPassword.equals("test-hash:" + rawPassword); }
  };
  private final se.cloudshop.audit.AuditService audit = mock(se.cloudshop.audit.AuditService.class);
  private final MfaLoginChallengeStore challenges = mock(MfaLoginChallengeStore.class);
  private final MfaService service = new MfaService(users, encoder, attempts, audit, challenges,
      Base64.getEncoder().encodeToString(new byte[32]));

  MfaServiceTest() {
    when(challenges.create(anyString())).thenAnswer(invocation -> java.util.UUID.randomUUID().toString());
    when(challenges.consume(anyString())).thenAnswer(invocation -> Optional.of("owner@example.com"));
  }

  @Test
  void enrollmentRequiresPasswordAndOnlyEnablesAfterValidTOTP() {
    User user = new User("owner@example.com", "test-hash:correct-horse");
    when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

    MfaService.MfaSetup setup = service.setup(user.getEmail(), "correct-horse");
    assertThat(setup.secret()).hasSize(32);
    assertThat(user.getMfaPendingSecretEnc()).startsWith("v1:").doesNotContain(setup.secret());
    assertThat(setup.otpauthUri()).contains("issuer=AliBooks");

    var enrollment = service.enable(user.getEmail(), currentCode(setup.secret()));
    assertThat(enrollment.recoveryCodes()).hasSize(10);
    assertThat(enrollment.authVersion()).isEqualTo(1);
    assertThat(user.isMfaEnabled()).isTrue();
    assertThat(user.getAuthVersion()).isEqualTo(1);
    assertThat(user.getMfaRecoveryHashes().split("\\n")).doesNotContain(enrollment.recoveryCodes().get(0));
  }

  @Test
  void recoveryCodeIsSingleUseAndTotpCounterCannotBeReplayed() {
    User user = new User("owner@example.com", "test-hash:correct-horse");
    when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
    MfaService.MfaSetup setup = service.setup(user.getEmail(), "correct-horse");
    String firstCode = currentCode(setup.secret());
    var recoveryCodes = service.enable(user.getEmail(), firstCode).recoveryCodes();

    String challenge = service.createLoginChallenge(user.getEmail());
    assertThat(service.completeLogin(challenge, recoveryCodes.get(0)))
        .isEqualTo(new MfaService.MfaLoginResult(user.getEmail(), 1));
    String replayChallenge = service.createLoginChallenge(user.getEmail());
    assertThatThrownBy(() -> service.completeLogin(replayChallenge, recoveryCodes.get(0)))
        .isInstanceOf(ResponseStatusException.class);

    String totpChallenge = service.createLoginChallenge(user.getEmail());
    assertThatThrownBy(() -> service.completeLogin(totpChallenge, firstCode)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void disablingAndReenablingMfaRevokesEarlierSessions() {
    User user = new User("owner@example.com", "test-hash:correct-horse");
    when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

    MfaService.MfaSetup first = service.setup(user.getEmail(), "correct-horse");
    var recovery = service.enable(user.getEmail(), currentCode(first.secret())).recoveryCodes();
    assertThat(user.getAuthVersion()).isEqualTo(1);
    service.disable(user.getEmail(), "correct-horse", recovery.get(0));
    assertThat(user.getAuthVersion()).isEqualTo(2);

    MfaService.MfaSetup second = service.setup(user.getEmail(), "correct-horse");
    service.enable(user.getEmail(), currentCode(second.secret()));
    assertThat(user.getAuthVersion()).isEqualTo(3);
  }

  private String currentCode(String secret) {
    try {
      byte[] key = decodeBase32(secret);
      long counter = java.time.Instant.now().getEpochSecond() / 30;
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
      int offset = hash[hash.length - 1] & 0x0f;
      int value = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
          | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
      return String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
    } catch (Exception exception) { throw new IllegalStateException(exception); }
  }

  private byte[] decodeBase32(String value) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    byte[] result = new byte[value.length() * 5 / 8];
    int buffer = 0, bits = 0, index = 0;
    for (char character : value.toCharArray()) {
      buffer = (buffer << 5) | alphabet.indexOf(character); bits += 5;
      if (bits >= 8) result[index++] = (byte) (buffer >> (bits -= 8));
    }
    return result;
  }
}
