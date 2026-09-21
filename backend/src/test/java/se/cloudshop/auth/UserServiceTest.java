package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class UserServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final UserService userService = new UserService(userRepository, passwordEncoder, "");
  private static final String SETUP_KEY = "test-owner-bootstrap-key-unique-2026";

  @Test
  void rejectsShortPasswordDuringRegistration() {
    assertThatThrownBy(() -> userService.register("demo@example.com", "1234567"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsInvalidEmailDuringRegistration() {
    assertThatThrownBy(() -> userService.register("not-an-email", "secret123"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void logsInExistingUserWithCorrectPassword() {
    User user = new User("demo@example.com", passwordEncoder.encode("secret123"));

    when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));

    assertThat(userService.login("demo@example.com", "secret123")).contains(user);
  }

  @Test
  void normalizesEmailDuringRegistrationAndLogin() {
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User registered = userService.register("  DEMO@EXAMPLE.COM  ", "secret123");

    assertThat(registered.getEmail()).isEqualTo("demo@example.com");
    verify(userRepository).existsByEmail("demo@example.com");
    verify(userRepository).lockRegistrationTable();

    User user = new User("demo@example.com", passwordEncoder.encode("secret123"));
    when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));

    assertThat(userService.login("  DEMO@EXAMPLE.COM  ", "secret123")).contains(user);
  }

  @Test
  void requiresConfiguredBootstrapKeyForFirstProductionAccount() {
    UserService protectedRegistration = new UserService(userRepository, passwordEncoder, SETUP_KEY);

    assertThatThrownBy(() -> protectedRegistration.register("owner@example.com", "secret123", "wrong-key"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED));

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void closesRegistrationOnceAnOwnerExistsEvenWithCorrectBootstrapKey() {
    when(userRepository.count()).thenReturn(1L);
    UserService protectedRegistration = new UserService(userRepository, passwordEncoder, SETUP_KEY);

    assertThatThrownBy(() -> protectedRegistration.register("other@example.com", "secret123", SETUP_KEY))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    verify(userRepository).lockRegistrationTable();
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void permitsFirstAccountWithCorrectBootstrapKey() {
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UserService protectedRegistration = new UserService(userRepository, passwordEncoder, SETUP_KEY);

    User owner = protectedRegistration.register("owner@example.com", "secret123", SETUP_KEY);

    assertThat(owner.getEmail()).isEqualTo("owner@example.com");
    verify(userRepository).save(any(User.class));
  }
}
