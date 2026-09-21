package se.cloudshop.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.audit.AuditService;

@RestController
public class AuthController {

  private final UserService userService;
  private final JwtService jwtService;
  private final LoginAttemptService loginAttemptService;
  private final AuditService auditService;

  public AuthController(
      UserService userService,
      JwtService jwtService,
      LoginAttemptService loginAttemptService,
      AuditService auditService
  ) {
    this.userService = userService;
    this.jwtService = jwtService;
    this.loginAttemptService = loginAttemptService;
    this.auditService = auditService;
  }

  @PostMapping("/auth/register")
  public Map<String, String> register(
      @RequestBody AuthRequest request,
      @RequestHeader(value = "X-AliBooks-Setup-Key", required = false) String setupKey
  ) {
    String email = request == null ? "" : request.email();
    User user;
    try {
      user = userService.register(email, request == null ? null : request.password(), setupKey);
    } catch (ResponseStatusException exception) {
      auditService.record(
          "auth",
          "user",
          normalizedEmail(email),
          "register_failed",
          normalizedEmail(email),
          exception.getReason() == null ? "Registration failed." : exception.getReason(),
          0,
          null
      );
      throw exception;
    }
    auditService.record("auth", "user", user.getEmail(), "register_success", user.getEmail(), "User registered.", 0, null);
    String token = jwtService.createToken(user.getEmail());

    return Map.of(
        "token", token,
        "email", user.getEmail()
    );
  }

  public Map<String, String> register(AuthRequest request) {
    return register(request, null);
  }

  @PostMapping("/auth/login")
  public Map<String, String> login(@RequestBody AuthRequest request) {
    String email = request == null ? "" : request.email();
    if (loginAttemptService.isBlocked(email)) {
      auditService.record(
          "auth",
          "user",
          normalizedEmail(email),
          "login_blocked",
          normalizedEmail(email),
          "Login blocked after repeated failed attempts.",
          safeAmount(loginAttemptService.remainingLockSeconds(email)),
          null
      );
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Try again later.");
    }

    User user = userService.login(email, request == null ? null : request.password())
        .orElseThrow(() -> {
          LoginAttemptResult result = loginAttemptService.recordFailure(email);
          auditService.record(
              "auth",
              "user",
              normalizedEmail(email),
              "login_failed",
              normalizedEmail(email),
              "Login failed.",
              result.failedAttempts(),
              null
          );
          HttpStatus status = result.blocked() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED;
          String message = result.blocked()
              ? "Too many failed login attempts. Try again later."
              : "Invalid email or password.";
          return new ResponseStatusException(status, message);
        });
    loginAttemptService.recordSuccess(user.getEmail());
    auditService.record("auth", "user", user.getEmail(), "login_success", user.getEmail(), "Login successful.", 0, null);
    String token = jwtService.createToken(user.getEmail());

    return Map.of(
        "token", token,
        "email", user.getEmail()
    );
  }

  private String normalizedEmail(String email) {
    return email == null || email.isBlank() ? "unknown" : email.trim().toLowerCase();
  }

  private int safeAmount(long amount) {
    if (amount > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) Math.max(amount, 0);
  }
}
