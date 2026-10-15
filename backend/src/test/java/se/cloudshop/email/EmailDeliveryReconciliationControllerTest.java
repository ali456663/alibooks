package se.cloudshop.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.auth.JwtService;

class EmailDeliveryReconciliationControllerTest {
  private final EmailDeliveryReconciliationService service = mock(EmailDeliveryReconciliationService.class);
  private final JwtService jwt = new JwtService("test_secret");
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new EmailDeliveryReconciliationController(new AuthHeader(jwt), service)).build();
  private final String authorization = "Bearer " + jwt.createToken("operator@example.test");
  private final EmailDeliverySummary summary = new EmailDeliverySummary(42L, "INVOICE_EMAIL", 7L,
      "customer@example.test", "Invoice", "UNCERTAIN", Instant.parse("2026-10-13T10:00:00Z"));

  @Test
  void listingAndResolutionRequireAuthentication() throws Exception {
    mvc.perform(get("/email-delivery/unresolved")).andExpect(status().isUnauthorized());
    mvc.perform(post("/email-delivery/42/resolve").contentType(MediaType.APPLICATION_JSON)
        .content("{\"outcome\":\"DELIVERED\",\"evidence\":\"Recipient confirmed delivery.\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void authenticatedResolutionPassesExternalEvidenceToService() throws Exception {
    when(service.unresolved()).thenReturn(List.of(summary));
    mvc.perform(get("/email-delivery/unresolved").header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("UNCERTAIN"));

    when(service.resolve(eq(42L), any(ResolveEmailDeliveryRequest.class), eq(authorization)))
        .thenReturn(summary);
    mvc.perform(post("/email-delivery/42/resolve").header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"outcome\":\"NOT_DELIVERED\",\"evidence\":\"SMTP provider rejected the message.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42));

    ArgumentCaptor<ResolveEmailDeliveryRequest> request = ArgumentCaptor.forClass(ResolveEmailDeliveryRequest.class);
    verify(service).resolve(eq(42L), request.capture(), eq(authorization));
    assertThat(request.getValue().outcome()).isEqualTo("NOT_DELIVERED");
    assertThat(request.getValue().evidence()).isEqualTo("SMTP provider rejected the message.");
  }
}
