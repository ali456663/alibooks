package se.cloudshop.payment;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.accounting.AccountingService;
import se.cloudshop.order.Order;
import se.cloudshop.order.OrderRepository;

@Service
public class StripePaymentService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final String stripeSecretKey;
  private final String webhookSecret;
  private final String frontendUrl;
  private final OrderRepository orderRepository;
  private final AccountingService accountingService;
  private final StripeWebhookEventRepository stripeWebhookEventRepository;

  public StripePaymentService(
      @Value("${stripe.secret-key}") String stripeSecretKey,
      @Value("${stripe.webhook-secret}") String webhookSecret,
      @Value("${app.frontend-url}") String frontendUrl,
      OrderRepository orderRepository,
      AccountingService accountingService,
      StripeWebhookEventRepository stripeWebhookEventRepository
  ) {
    this.stripeSecretKey = stripeSecretKey;
    this.webhookSecret = webhookSecret;
    this.frontendUrl = frontendUrl;
    this.orderRepository = orderRepository;
    this.accountingService = accountingService;
    this.stripeWebhookEventRepository = stripeWebhookEventRepository;
  }

  public CheckoutSessionResponse createCheckoutSession(Long invoiceId) {
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured. Set STRIPE_SECRET_KEY.");
    }

    Order invoice = orderRepository.findById(invoiceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

    Stripe.apiKey = stripeSecretKey;

    SessionCreateParams params = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.PAYMENT)
        .setSuccessUrl(frontendUrl + "?payment=success&invoiceId=" + invoice.getId())
        .setCancelUrl(frontendUrl + "?payment=cancelled&invoiceId=" + invoice.getId())
        .setClientReferenceId(String.valueOf(invoice.getId()))
        .putMetadata("invoiceId", String.valueOf(invoice.getId()))
        .addLineItem(
            SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(
                    SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency("sek")
                        .setUnitAmount((long) invoice.getTotalAmount() * 100)
                        .setProductData(
                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName("Invoice #" + invoice.getId() + " - " + invoice.getProduct().getName())
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build();

    try {
      Session session = Session.create(params);
      invoice.setStripeCheckoutSessionId(session.getId());
      orderRepository.save(invoice);
      return new CheckoutSessionResponse(session.getUrl());
    } catch (StripeException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Stripe Checkout Session.");
    }
  }

  @Transactional
  public void handleWebhook(String payload, String signatureHeader) {
    if (webhookSecret == null || webhookSecret.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook secret is not configured.");
    }

    try {
      Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);

      if (stripeWebhookEventRepository.existsById(event.getId())) {
        return;
      }

      if (!"checkout.session.completed".equals(event.getType())) {
        stripeWebhookEventRepository.save(new StripeWebhookEvent(event.getId(), event.getType()));
        return;
      }

      JsonNode root = OBJECT_MAPPER.readTree(payload);
      JsonNode session = root.path("data").path("object");
      String invoiceId = session.path("metadata").path("invoiceId").asText();

      if (invoiceId == null || invoiceId.isBlank()) {
        String clientReferenceId = session.path("client_reference_id").asText();
        if (isNumeric(clientReferenceId)) {
          invoiceId = clientReferenceId;
        }
      }

      if (invoiceId != null && !invoiceId.isBlank()) {
        Order invoice = orderRepository.findById(Long.valueOf(invoiceId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

        if (!"PAID".equals(invoice.getStatus())) {
          accountingService.createPaymentEntries(invoice);
          invoice.setStatus("PAID");
          orderRepository.save(invoice);
        }
      } else {
        int totalAmount = Math.toIntExact(session.path("amount_total").asLong() / 100L);
        String currency = session.path("currency").asText("sek");
        if (!"sek".equalsIgnoreCase(currency)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only SEK Stripe sales can be booked automatically.");
        }

        String stripeReference = session.path("payment_intent").asText();
        if (stripeReference == null || stripeReference.isBlank()) {
          stripeReference = session.path("id").asText();
        }
        accountingService.createStripeExternalSaleEntries(totalAmount, stripeReference, LocalDate.now());
      }

      stripeWebhookEventRepository.save(new StripeWebhookEvent(event.getId(), event.getType()));
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook.");
    }
  }

  private boolean isNumeric(String value) {
    return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
  }
}
