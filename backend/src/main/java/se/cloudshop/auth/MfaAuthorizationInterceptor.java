package se.cloudshop.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class MfaAuthorizationInterceptor implements HandlerInterceptor {
  private final JwtService jwtService;

  public MfaAuthorizationInterceptor(JwtService jwtService) { this.jwtService = jwtService; }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")
        && jwtService.mfaVerified(authorization.substring("Bearer ".length()))) return true;
    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Complete MFA sign-in before accessing protected AliBooks data.");
    return false;
  }
}
