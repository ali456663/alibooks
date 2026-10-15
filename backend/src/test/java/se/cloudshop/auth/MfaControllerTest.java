package se.cloudshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.cloudshop.audit.AuditService;

class MfaControllerTest {
  @Test
  void enrollmentReturnsVerifiedTokenForNewAuthenticationVersion() {
    JwtService jwt = new JwtService("test-secret");
    MfaService mfa = mock(MfaService.class);
    when(mfa.enable("owner@example.test", "123456"))
        .thenReturn(new MfaService.MfaEnrollment(List.of("RECOVERY-CODE"), 2));
    MfaController controller = new MfaController(mfa, mock(UserService.class), jwt,
        new AuthHeader(jwt), mock(AuditService.class));

    Map<String, Object> response = controller.enable(
        "Bearer " + jwt.createToken("owner@example.test", false, 1),
        new MfaController.CodeRequest("123456"));

    assertThat(response.get("recoveryCodes")).isEqualTo(List.of("RECOVERY-CODE"));
    String newToken = (String) response.get("token");
    assertThat(jwt.mfaVerified(newToken)).isTrue();
    assertThat(jwt.authVersion(newToken)).hasValue(2);
  }
}
