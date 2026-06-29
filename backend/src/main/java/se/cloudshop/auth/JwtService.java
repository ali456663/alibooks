package se.cloudshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String secret;

  public JwtService(@Value("${jwt.secret}") String secret) {
    this.secret = secret;
  }

  public String createToken(String email) {
    try {
      String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
      String payload = encodeJson(Map.of(
          "sub", email,
          "iat", Instant.now().getEpochSecond(),
          "exp", Instant.now().plusSeconds(3600).getEpochSecond()
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
      return sign(unsignedToken).equals(parts[2]);
    } catch (Exception exception) {
      return false;
    }
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
}

