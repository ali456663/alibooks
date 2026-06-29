package se.cloudshop.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

  private final UserService userService;
  private final JwtService jwtService;

  public AuthController(UserService userService, JwtService jwtService) {
    this.userService = userService;
    this.jwtService = jwtService;
  }

  @PostMapping("/auth/register")
  public Map<String, String> register(@RequestBody AuthRequest request) {
    User user = userService.register(request.email(), request.password());
    String token = jwtService.createToken(user.getEmail());

    return Map.of(
        "token", token,
        "email", user.getEmail()
    );
  }

  @PostMapping("/auth/login")
  public Map<String, String> login(@RequestBody AuthRequest request) {
    User user = userService.login(request.email(), request.password())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
    String token = jwtService.createToken(user.getEmail());

    return Map.of(
        "token", token,
        "email", user.getEmail()
    );
  }
}
