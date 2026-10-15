package se.cloudshop.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.web.servlet.HandlerInterceptor;
import se.cloudshop.auth.JwtService;
import se.cloudshop.auth.UserRepository;

public class ProductionAccountRequestGuard implements HandlerInterceptor {
  private static final Set<String> MFA_SETUP_PATHS = Set.of(
      "/auth/mfa/status", "/auth/mfa/setup", "/auth/mfa/enable", "/auth/mfa/cancel", "/auth/mfa/disable");
  private final UserRepository users;
  private final JwtService jwt;

  public ProductionAccountRequestGuard(UserRepository users, JwtService jwt) {
    this.users = users;
    this.jwt = jwt;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

    long count = users.count();
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (count == 0 && "POST".equalsIgnoreCase(request.getMethod()) && "/auth/register".equals(path)) return true;
    if (count != 1) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
          "Accounting access requires exactly one owner account.");
      return false;
    }

    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      String token = authorization.substring("Bearer ".length());
      if (jwt.isValid(token)) {
        var owner = jwt.subject(token).flatMap(users::findByEmail);
        if (owner.isEmpty()) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token owner no longer exists.");
          return false;
        }
        var tokenVersion = jwt.authVersion(token);
        if (tokenVersion.isEmpty() || tokenVersion.getAsLong() != owner.get().getAuthVersion()) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session was revoked. Sign in again.");
          return false;
        }
        if (!owner.get().isMfaEnabled() && !MFA_SETUP_PATHS.contains(path)) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN, "Enable MFA before accessing accounting data.");
          return false;
        }
      }
    }
    return true;
  }
}
