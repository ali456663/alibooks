package se.cloudshop.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;

@Service
public class MfaService {
  private static final int PERIOD_SECONDS = 30;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttempts;
  private final AuditService audit;
  private final MfaLoginChallengeStore challenges;
  private final byte[] encryptionKey;

  public MfaService(UserRepository users, PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttempts,
      AuditService audit,
      MfaLoginChallengeStore challenges,
      @Value("${app.auth.mfa-encryption-key:}") String encodedKey) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.loginAttempts = loginAttempts;
    this.audit = audit;
    this.challenges = challenges;
    byte[] key;
    try {
      key = encodedKey == null || encodedKey.isBlank() ? new byte[0] : Base64.getDecoder().decode(encodedKey.trim());
    } catch (IllegalArgumentException exception) {
      key = new byte[0];
    }
    this.encryptionKey = key.length == 32 ? key : new byte[0];
  }

  public boolean isEnabled(String email) {
    return users.findByEmail(email).map(User::isMfaEnabled).orElse(false);
  }

  @Transactional
  public MfaSetup setup(String email, String password) {
    requireEncryptionKey();
    User user = users.findByEmailForUpdate(email).orElseThrow(MfaService::notFound);
    if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is invalid.");
    }
    if (user.isMfaEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA is already enabled.");
    }
    String secret = generateSecret();
    user.setMfaPendingSecretEnc(encrypt(secret));
    return new MfaSetup(secret, "otpauth://totp/AliBooks:" + urlEncode(user.getEmail())
        + "?secret=" + secret + "&issuer=AliBooks&algorithm=SHA1&digits=6&period=30");
  }

  @Transactional
  public MfaEnrollment enable(String email, String code) {
    requireEncryptionKey();
    User user = users.findByEmailForUpdate(email).orElseThrow(MfaService::notFound);
    if (user.isMfaEnabled() || user.getMfaPendingSecretEnc() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Start MFA setup before confirming it.");
    }
    String secret = decrypt(user.getMfaPendingSecretEnc());
    long counter = matchingCounter(secret, code, -1);
    if (counter < 0) throw invalidCode();
    List<String> recovery = new ArrayList<>();
    List<String> hashes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String value = randomRecoveryCode();
      recovery.add(value);
      hashes.add(passwordEncoder.encode(value));
    }
    user.setMfaSecretEnc(user.getMfaPendingSecretEnc());
    user.setMfaPendingSecretEnc(null);
    user.setMfaRecoveryHashes(String.join("\n", hashes));
    user.setMfaLastCounter(counter);
    user.setMfaEnabled(true);
    user.rotateAuthVersion();
    return new MfaEnrollment(List.copyOf(recovery), user.getAuthVersion());
  }

  @Transactional
  public void cancelSetup(String email) {
    User user = users.findByEmailForUpdate(email).orElseThrow(MfaService::notFound);
    if (!user.isMfaEnabled()) user.setMfaPendingSecretEnc(null);
  }

  @Transactional
  public void disable(String email, String password, String code) {
    requireEncryptionKey();
    User user = users.findByEmailForUpdate(email).orElseThrow(MfaService::notFound);
    if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword()) || !user.isMfaEnabled()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password or verification code is invalid.");
    }
    if (!consumeCode(user, code)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password or verification code is invalid.");
    clearMfa(user);
  }

  public String createLoginChallenge(String email) {
    if (loginAttempts.isBlocked(email)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Try again later.");
    return challenges.create(email);
  }

  @Transactional
  public MfaLoginResult completeLogin(String challenge, String code) {
    String email = challenges.consume(challenge).orElseThrow(MfaService::invalidCode);
    if (loginAttempts.isBlocked(email)) {
      loginAttempts.recordFailure(email);
      audit.recordMfaLoginFailure(email, "MFA login challenge was blocked.");
      throw invalidCode();
    }
    User user = users.findByEmailForUpdate(email).orElseThrow(MfaService::notFound);
    if (!user.isMfaEnabled() || !consumeCode(user, code)) {
      loginAttempts.recordFailure(email);
      audit.recordMfaLoginFailure(email, "MFA verification failed.");
      throw invalidCode();
    }
    loginAttempts.recordSuccess(user.getEmail());
    return new MfaLoginResult(user.getEmail(), user.getAuthVersion());
  }

  private boolean consumeCode(User user, String supplied) {
    if (supplied == null) return false;
    String normalized = supplied.trim().toUpperCase(Locale.ROOT);
    if (normalized.matches("[0-9]{6}")) {
      long counter = matchingCounter(decrypt(user.getMfaSecretEnc()), normalized, user.getMfaLastCounter());
      if (counter < 0) return false;
      user.setMfaLastCounter(counter);
      return true;
    }
    String stored = user.getMfaRecoveryHashes();
    if (stored == null || stored.isBlank() || normalized.length() > 64) return false;
    String[] hashes = stored.split("\\n");
    for (int i = 0; i < hashes.length; i++) {
      if (passwordEncoder.matches(normalized, hashes[i])) {
        hashes[i] = "!used!";
        user.setMfaRecoveryHashes(String.join("\n", hashes));
        return true;
      }
    }
    return false;
  }

  private long matchingCounter(String secret, String code, long lastCounter) {
    if (code == null || !code.trim().matches("[0-9]{6}")) return -1;
    long current = Instant.now().getEpochSecond() / PERIOD_SECONDS;
    for (long counter = current + 1; counter >= Math.max(0, current - 1); counter--) {
      if (counter > lastCounter && MessageDigest.isEqual(totp(secret, counter).getBytes(StandardCharsets.US_ASCII),
          code.trim().getBytes(StandardCharsets.US_ASCII))) return counter;
    }
    return -1;
  }

  private String totp(String secret, long counter) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
      int offset = hash[hash.length - 1] & 0x0f;
      int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
          | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
      return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
    } catch (Exception exception) {
      throw new IllegalStateException("TOTP generation failed.", exception);
    }
  }

  private String encrypt(String value) {
    try {
      byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      byte[] packed = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
      return "v1:" + Base64.getEncoder().encodeToString(packed);
    } catch (Exception exception) { throw new IllegalStateException("MFA secret encryption failed.", exception); }
  }

  private String decrypt(String value) {
    requireEncryptionKey();
    try {
      if (value == null || !value.startsWith("v1:")) throw new IllegalStateException("Unsupported MFA secret format.");
      byte[] packed = Base64.getDecoder().decode(value.substring(3));
      byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
      byte[] ciphertext = java.util.Arrays.copyOfRange(packed, 12, packed.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception exception) { throw new IllegalStateException("MFA secret cannot be decrypted; refusing authentication.", exception); }
  }

  private void requireEncryptionKey() {
    if (encryptionKey.length != 32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "MFA is unavailable until APP_AUTH_MFA_ENCRYPTION_KEY is configured as base64 for 32 random bytes.");
  }

  private String generateSecret() {
    byte[] bytes = new byte[20]; RANDOM.nextBytes(bytes); return encodeBase32(bytes);
  }

  private String randomRecoveryCode() {
    byte[] bytes = new byte[8]; RANDOM.nextBytes(bytes);
    return HexFormat.of().withUpperCase().formatHex(bytes, 0, 4) + "-" + HexFormat.of().withUpperCase().formatHex(bytes, 4, 8);
  }

  private String encodeBase32(byte[] bytes) {
    StringBuilder out = new StringBuilder(); int buffer = 0, bits = 0;
    for (byte value : bytes) { buffer = (buffer << 8) | (value & 0xff); bits += 8;
      while (bits >= 5) { out.append(BASE32[(buffer >> (bits -= 5)) & 31]); } }
    if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
    return out.toString();
  }

  private byte[] decodeBase32(String value) {
    byte[] output = new byte[value.length() * 5 / 8]; int buffer = 0, bits = 0, index = 0;
    for (char character : value.toUpperCase(Locale.ROOT).toCharArray()) {
      int digit = new String(BASE32).indexOf(character);
      if (digit < 0) throw new IllegalArgumentException("Invalid Base32 secret.");
      buffer = (buffer << 5) | digit; bits += 5;
      if (bits >= 8) output[index++] = (byte) (buffer >> (bits -= 8));
    }
    return output;
  }

  private String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private void clearMfa(User user) {
    user.setMfaEnabled(false); user.setMfaSecretEnc(null); user.setMfaPendingSecretEnc(null);
    user.setMfaRecoveryHashes(null); user.setMfaLastCounter(-1);
    user.rotateAuthVersion();
  }

  private static ResponseStatusException invalidCode() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code.");
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found.");
  }

  public record MfaSetup(String secret, String otpauthUri) {}
  public record MfaEnrollment(List<String> recoveryCodes, long authVersion) {}
  public record MfaLoginResult(String email, long authVersion) {}
}
