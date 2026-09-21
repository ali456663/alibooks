package se.cloudshop.payment;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
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

    if (invoice.isCreditInvoice() || "CREDITED".equals(invoice.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe checkout cannot be created for credit invoices or credited invoices.");
    }

    if (!invoice.hasRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice has no remaining amount to pay.");
    }
    long remainingAmountMinor = invoice.getRemainingAmountMinor();
    if (remainingAmountMinor % 100L != 0) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Stripe checkout cannot represent an invoice balance with ore. Reconcile the invoice manually.");
    }

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
                        .setUnitAmount(remainingAmountMinor)
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
      Session session = new StripeClient(stripeSecretKey).checkout().sessions().create(params);
      // Do not merge a detached invoice here: a payment could have arrived during the API call.
      orderRepository.updateStripeCheckoutSessionId(invoiceId, session.getId());
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

    Event event;
    JsonNode root;
    try {
      event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
      root = OBJECT_MAPPER.readTree(payload);
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook.");
    }

    if (event.getId() == null || event.getId().isBlank() || event.getType() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe event identity is missing.");
    }
    stripeWebhookEventRepository.lockProcessingKey(event.getId());
    if (stripeWebhookEventRepository.existsById(event.getId())) return;

    if (!"checkout.session.completed".equals(event.getType())
        && !"checkout.session.async_payment_succeeded".equals(event.getType())) {
      recordEvent(event);
      return;
    }
    JsonNode session = root.path("data").path("object");
    if ("unpaid".equals(session.path("payment_status").asText())
        && "checkout.session.completed".equals(event.getType())) {
      recordEvent(event); // Delayed payment: only the later paid event may book it.
      return;
    }
    if (!"paid".equals(session.path("payment_status").asText())
        || !"payment".equals(session.path("mode").asText())) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Stripe session is not a confirmed one-time payment.");
    }
    String sessionId = session.path("id").asText();
    if (!sessionId.startsWith("cs_") || sessionId.length() > 240) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe checkout session identity is missing.");
    }
    String sessionKey = "checkout:" + sessionId;
    stripeWebhookEventRepository.lockProcessingKey(sessionKey);
    if (stripeWebhookEventRepository.existsById(sessionKey)) {
      recordEvent(event);
      return;
    }
    if (!"sek".equalsIgnoreCase(session.path("currency").asText())) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only SEK Stripe sales can be booked automatically.");
    }
    JsonNode amount = session.path("amount_total");
    if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0
        || amount.longValue() % 100 != 0 || amount.longValue() / 100 > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Stripe amount must be positive whole SEK within the supported range. Reconcile minor units manually.");
    }
    int paidAmount = Math.toIntExact(amount.longValue() / 100);
    String invoiceId = session.path("metadata").path("invoiceId").asText();
    long id;
    try {
      id = Long.parseLong(invoiceId);
      if (id <= 0) throw new NumberFormatException();
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Stripe payment requires an AliBooks invoice. External sales require reviewed tax and accounting data.");
    }
    orderRepository.lockById(id);
    Order invoice = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
    if (invoice.isCreditInvoice() || !("SENT".equals(invoice.getStatus()) || "PARTIALLY_PAID".equals(invoice.getStatus()))
        || paidAmount > invoice.getRemainingAmount()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stripe payment conflicts with invoice status or remaining amount. Reconcile manually.");
    }
    if (event.getCreated() == null || event.getCreated() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe event date is missing.");
    }
    LocalDate paymentDate = Instant.ofEpochSecond(event.getCreated()).atZone(ZoneId.of("Europe/Stockholm")).toLocalDate();
    accountingService.createStripeInvoicePaymentEntries(invoice, paymentDate, paidAmount);
    invoice.registerPayment(paymentDate, paidAmount, "Stripe " + sessionId);
    orderRepository.save(invoice);
    stripeWebhookEventRepository.save(new StripeWebhookEvent(sessionKey, "checkout.session.booked"));
    recordEvent(event);
  }

  private void recordEvent(Event event) {
    stripeWebhookEventRepository.save(new StripeWebhookEvent(event.getId(), event.getType()));
  }
}
