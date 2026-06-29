package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class UserServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final UserService userService = new UserService(userRepository, passwordEncoder);

  @Test
  void rejectsShortPasswordDuringRegistration() {
    assertThatThrownBy(() -> userService.register("demo@example.com", "123"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void logsInExistingUserWithCorrectPassword() {
    User user = new User("demo@example.com", passwordEncoder.encode("secret123"));

    when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));

    assertThat(userService.login("demo@example.com", "secret123")).contains(user);
  }
}
