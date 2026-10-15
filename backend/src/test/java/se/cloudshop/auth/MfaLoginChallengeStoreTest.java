package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MfaLoginChallengeStoreTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final MfaLoginChallengeStore store = new MfaLoginChallengeStore(jdbc);

  @Test
  void persistsOnlyChallengeDigestAndConsumesItWithAtomicDelete() throws Exception {
    when(jdbc.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class))).thenReturn(0);
    when(jdbc.queryForList(contains("DELETE FROM mfa_login_challenges"), eq(String.class), any()))
        .thenReturn(List.of("owner@example.com"));

    String challenge = store.create("owner@example.com");
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(challenge.getBytes(StandardCharsets.UTF_8)));

    assertThat(challenge).isNotEqualTo(digest);
    verify(jdbc).update(contains("INSERT INTO mfa_login_challenges"), eq(digest),
        eq("owner@example.com"), eq(300));
    assertThat(store.consume(challenge)).contains("owner@example.com");
    verify(jdbc).queryForList(contains("DELETE FROM mfa_login_challenges"), eq(String.class), eq(digest));
  }

  @Test
  void challengeConsumptionCommitsOutsideFailedMfaAttemptTransaction() throws Exception {
    Transactional create = MfaLoginChallengeStore.class.getMethod("create", String.class)
        .getAnnotation(Transactional.class);
    Transactional consume = MfaLoginChallengeStore.class.getMethod("consume", String.class)
        .getAnnotation(Transactional.class);

    assertThat(create.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    assertThat(consume.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}
