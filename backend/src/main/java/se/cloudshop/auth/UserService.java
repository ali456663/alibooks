package se.cloudshop.auth;

import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String registrationBootstrapKey;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.auth.registration-bootstrap-key:}") String registrationBootstrapKey
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.registrationBootstrapKey = registrationBootstrapKey == null ? "" : registrationBootstrapKey;
  }

  @Transactional
  public User register(String email, String password) {
    return register(email, password, null);
  }

  @Transactional
  public User register(String email, String password, String suppliedBootstrapKey) {
    String normalizedEmail = normalizeEmail(email);
    if (!isValidEmail(normalizedEmail) || password == null || password.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email and password with at least 8 characters are required.");
    }

    userRepository.lockRegistrationTable();
    if (userRepository.count() > 0) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Registration is closed. This AliBooks workspace already has its owner account."
      );
    }

    if (!registrationBootstrapKey.isBlank() && !matchesBootstrapKey(suppliedBootstrapKey)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid account setup key is required for the first account.");
    }

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
    }

    return userRepository.save(new User(normalizedEmail, passwordEncoder.encode(password)));
  }

  private boolean matchesBootstrapKey(String suppliedKey) {
    if (suppliedKey == null) return false;
    return MessageDigest.isEqual(
        registrationBootstrapKey.getBytes(StandardCharsets.UTF_8),
        suppliedKey.getBytes(StandardCharsets.UTF_8)
    );
  }

  public Optional<User> login(String email, String password) {
    if (email == null || password == null) {
      return Optional.empty();
    }

    return userRepository.findByEmail(normalizeEmail(email))
        .filter(user -> passwordEncoder.matches(password, user.getPassword()));
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private boolean isValidEmail(String email) {
    return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  }
}
