package se.cloudshop.auth;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public User register(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    if (!isValidEmail(normalizedEmail) || password == null || password.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email and password with at least 8 characters are required.");
    }

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
    }

    return userRepository.save(new User(normalizedEmail, passwordEncoder.encode(password)));
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
