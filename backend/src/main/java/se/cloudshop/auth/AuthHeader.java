package se.cloudshop.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthHeader {

  private final JwtService jwtService;

  public AuthHeader(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  public void requireValidToken(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token is required.");
    }

    String token = authorizationHeader.substring("Bearer ".length());

    if (!jwtService.isValid(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token is invalid.");
    }
  }
}

