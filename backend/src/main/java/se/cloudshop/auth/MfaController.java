package se.cloudshop.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;

@RestController
public class MfaController {
  private final MfaService mfa;
  private final UserService users;
  private final JwtService jwt;
  private final AuthHeader authHeader;
  private final AuditService audit;

  public MfaController(MfaService mfa, UserService users, JwtService jwt, AuthHeader authHeader, AuditService audit) {
    this.mfa = mfa;
    this.users = users;
    this.jwt = jwt;
    this.authHeader = authHeader;
    this.audit = audit;
  }

  @GetMapping("/auth/mfa/status")
  public Map<String, Boolean> status(@RequestHeader(value = "Authorization", required = false) String authorization) {
    return Map.of("enabled", mfa.isEnabled(email(authorization)));
  }

  @PostMapping("/auth/mfa/setup")
  public MfaService.MfaSetup setup(@RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody PasswordRequest request) {
    String email = email(authorization);
    if (users.login(email, request == null ? null : request.password()).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is invalid.");
    }
    MfaService.MfaSetup setup = mfa.setup(email, request.password());
    audit.record("security", "user", email, "mfa_setup_started", email, "MFA enrollment started.", 0, authorization);
    return setup;
  }

  @PostMapping("/auth/mfa/enable")
  public Map<String, Object> enable(@RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody CodeRequest request) {
    String email = email(authorization);
    MfaService.MfaEnrollment enrollment = mfa.enable(email, request == null ? null : request.code());
    audit.record("security", "user", email, "mfa_enabled", email, "MFA enabled.", 0, authorization);
    return Map.of(
        "recoveryCodes", enrollment.recoveryCodes(),
        "token", jwt.createToken(email, true, enrollment.authVersion()));
  }

  @PostMapping("/auth/mfa/cancel")
  public Map<String, Boolean> cancel(@RequestHeader(value = "Authorization", required = false) String authorization) {
    String email = email(authorization);
    mfa.cancelSetup(email);
    return Map.of("cancelled", true);
  }

  @PostMapping("/auth/mfa/disable")
  public Map<String, Boolean> disable(@RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody DisableRequest request) {
    String email = email(authorization);
    mfa.disable(email, request == null ? null : request.password(), request == null ? null : request.code());
    audit.record("security", "user", email, "mfa_disabled", email, "MFA disabled after re-authentication.", 0, authorization);
    return Map.of("enabled", false);
  }

  private String email(String authorization) {
    authHeader.requireValidToken(authorization);
    return jwt.subject(authorization.substring("Bearer ".length()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is invalid."));
  }

  public record PasswordRequest(String password) {}
  public record CodeRequest(String code) {}
  public record DisableRequest(String password, String code) {}
}
