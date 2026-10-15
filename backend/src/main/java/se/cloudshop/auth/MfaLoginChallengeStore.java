package se.cloudshop.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class MfaLoginChallengeStore {
  private static final int TTL_SECONDS = 300;
  private static final int MAX_ACTIVE_CHALLENGES = 10_000;
  private final JdbcTemplate jdbcTemplate;

  public MfaLoginChallengeStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String create(String email) {
    jdbcTemplate.update("DELETE FROM mfa_login_challenges WHERE expires_at <= CURRENT_TIMESTAMP");
    Integer active = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM mfa_login_challenges WHERE expires_at > CURRENT_TIMESTAMP", Integer.class);
    if (active != null && active >= MAX_ACTIVE_CHALLENGES) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MFA login is temporarily unavailable.");
    }
    String challenge = UUID.randomUUID().toString() + UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO mfa_login_challenges (challenge_hash, user_email, expires_at) "
        + "VALUES (?, ?, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))",
        hash(challenge), email, TTL_SECONDS);
    return challenge;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<String> consume(String challenge) {
    if (challenge == null || challenge.isBlank()) return Optional.empty();
    List<String> rows = jdbcTemplate.queryForList(
        "DELETE FROM mfa_login_challenges WHERE challenge_hash = ? AND expires_at > CURRENT_TIMESTAMP "
            + "RETURNING user_email", String.class, hash(challenge));
    return rows.stream().findFirst();
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }
}
