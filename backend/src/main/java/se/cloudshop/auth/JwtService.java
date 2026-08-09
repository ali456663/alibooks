package se.cloudshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
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
    try {
      Instant now = Instant.now();
      String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
      String payload = encodeJson(Map.of(
          "sub", email,
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

  public boolean isValid(String token) {
    try {
      String[] parts = token.split("\\.");

      if (parts.length != 3) {
        return false;
      }

      String unsignedToken = parts[0] + "." + parts[1];
      return sign(unsignedToken).equals(parts[2]) && !isExpired(parts[1]);
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
