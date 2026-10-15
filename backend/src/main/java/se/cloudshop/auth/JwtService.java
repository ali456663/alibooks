package se.cloudshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String secret;
  private final long expirationSeconds;

  public JwtService(String secret) {
    this(secret, 60);
  }

  @Autowired
  public JwtService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration-minutes:60}") int expirationMinutes
  ) {
    this.secret = secret;
    this.expirationSeconds = Math.max(expirationMinutes, 1) * 60L;
  }

  public String createToken(String email) {
    return createToken(email, false);
  }

  public String createToken(String email, boolean mfaVerified) {
    return createToken(email, mfaVerified, 0);
  }

  public String createToken(String email, boolean mfaVerified, long authVersion) {
    if (authVersion < 0) throw new IllegalArgumentException("Invalid authentication version.");
    try {
      Instant now = Instant.now();
      String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
      String payload = encodeJson(Map.of(
          "sub", email,
          "mfa", mfaVerified,
          "ver", authVersion,
          "iat", now.getEpochSecond(),
          "exp", now.plusSeconds(expirationSeconds).getEpochSecond()
      ));
      String unsignedToken = header + "." + payload;
      String signature = sign(unsignedToken);

      return unsignedToken + "." + signature;
    } catch (Exception exception) {
      throw new IllegalStateException("Could not create JWT.", exception);
    }
  }

  public boolean mfaVerified(String token) {
    if (!isValid(token)) return false;
    try {
      Map<?, ?> payload = OBJECT_MAPPER.readValue(decodeBase64Url(token.split("\\.")[1]), Map.class);
      return Boolean.TRUE.equals(payload.get("mfa"));
    } catch (Exception exception) {
      return false;
    }
  }

  public OptionalLong authVersion(String token) {
    if (!isValid(token)) return OptionalLong.empty();
    try {
      Map<?, ?> payload = OBJECT_MAPPER.readValue(decodeBase64Url(token.split("\\.")[1]), Map.class);
      Object version = payload.get("ver");
      if (!(version instanceof Integer || version instanceof Long)) return OptionalLong.empty();
      long value = ((Number) version).longValue();
      return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    } catch (Exception exception) {
      return OptionalLong.empty();
    }
  }

  public boolean isValid(String token) {
    try {
      String[] parts = token.split("\\.");

      if (parts.length != 3) {
        return false;
      }

      String unsignedToken = parts[0] + "." + parts[1];
      byte[] expectedSignature = sign(unsignedToken).getBytes(StandardCharsets.US_ASCII);
      byte[] suppliedSignature = parts[2].getBytes(StandardCharsets.US_ASCII);
      return MessageDigest.isEqual(expectedSignature, suppliedSignature) && !isExpired(parts[1]);
    } catch (Exception exception) {
      return false;
    }
  }

  public Optional<String> subject(String token) {
    if (!isValid(token)) {
      return Optional.empty();
    }

    try {
      Map<?, ?> payload = OBJECT_MAPPER.readValue(decodeBase64Url(token.split("\\.")[1]), Map.class);
      Object subject = payload.get("sub");
      if (subject == null || String.valueOf(subject).isBlank()) {
        return Optional.empty();
      }
      return Optional.of(String.valueOf(subject));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  public long expirationMinutes() {
    return expirationSeconds / 60L;
  }

  private String encodeJson(Map<String, Object> value) throws Exception {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
  }

  private String sign(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  }

  private boolean isExpired(String payloadPart) throws Exception {
    Map<?, ?> payload = OBJECT_MAPPER.readValue(decodeBase64Url(payloadPart), Map.class);
    Object expiresAt = payload.get("exp");
    if (expiresAt == null) {
      return true;
    }

    long expiresAtEpochSecond = Long.parseLong(String.valueOf(expiresAt));
    return Instant.now().getEpochSecond() >= expiresAtEpochSecond;
  }

  private byte[] decodeBase64Url(String value) {
    return Base64.getUrlDecoder().decode(value);
  }
}
