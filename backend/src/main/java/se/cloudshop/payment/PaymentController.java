package se.cloudshop.payment;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class PaymentController {

  private final AuthHeader authHeader;
  private final StripePaymentService stripePaymentService;

  public PaymentController(AuthHeader authHeader, StripePaymentService stripePaymentService) {
    this.authHeader = authHeader;
    this.stripePaymentService = stripePaymentService;
  }

  @PostMapping("/invoices/{id}/checkout-session")
  public CheckoutSessionResponse createCheckoutSession(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long id
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return stripePaymentService.createCheckoutSession(id);
  }

  @PostMapping("/stripe/webhook")
  public void handleStripeWebhook(
      @RequestBody String payload,
      @RequestHeader("Stripe-Signature") String signatureHeader
  ) {
    stripePaymentService.handleWebhook(payload, signatureHeader);
  }
}
