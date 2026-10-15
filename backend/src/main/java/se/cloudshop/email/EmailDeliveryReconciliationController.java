package se.cloudshop.email;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.auth.AuthHeader;

@RestController
@RequestMapping("/email-delivery")
public class EmailDeliveryReconciliationController {

  private final AuthHeader auth;
  private final EmailDeliveryReconciliationService reconciliation;

  public EmailDeliveryReconciliationController(AuthHeader auth, EmailDeliveryReconciliationService reconciliation) {
    this.auth = auth;
    this.reconciliation = reconciliation;
  }

  @GetMapping("/unresolved")
  public List<EmailDeliverySummary> unresolved(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    auth.requireValidToken(authorization);
    return reconciliation.unresolved();
  }

  @PostMapping("/{attemptId}/resolve")
  public EmailDeliverySummary resolve(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable Long attemptId,
      @RequestBody(required = false) ResolveEmailDeliveryRequest request) {
    auth.requireValidToken(authorization);
    if (attemptId == null || attemptId <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery attempt id is required.");
    }
    return reconciliation.resolve(attemptId, request, authorization);
  }
}
