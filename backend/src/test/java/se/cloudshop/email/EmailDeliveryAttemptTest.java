package se.cloudshop.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EmailDeliveryAttemptTest {

  @Test
  void storesAttachmentFingerprintAndMarksUncertainDelivery() {
    byte[] attachment = "pdf".getBytes(StandardCharsets.UTF_8);
    EmailDeliveryAttempt attempt = new EmailDeliveryAttempt(
        "INVOICE_EMAIL", 7L, "customer@example.com", "Invoice 1", "Body", "invoice.pdf", attachment
    );

    assertThat(attempt.getStatus()).isEqualTo("PENDING");
    assertThat(attempt.getAttachmentContent()).isEqualTo(attachment);
    assertThat(attempt.getAttachmentSha256()).isEqualTo(
        "c35b21d6ca39aa7cc3b79a705d989f1a6e88b99ab43988d74048799e3db926a3"
    );

    attempt.markUncertain(new RuntimeException("SMTP timeout"));

    assertThat(attempt.getStatus()).isEqualTo("UNCERTAIN");
    assertThat(attempt.getLastError()).isEqualTo("SMTP timeout");
    assertThat(attempt.getSentAt()).isNull();
  }

  @Test
  void marksSuccessfulDeliveryWithoutKeepingAnError() {
    EmailDeliveryAttempt attempt = new EmailDeliveryAttempt(
        "INVOICE_EMAIL", 7L, "customer@example.com", "Invoice 1", "Body", null, null
    );
    attempt.markUncertain(new RuntimeException("SMTP timeout"));

    attempt.markSent();

    assertThat(attempt.getStatus()).isEqualTo("SENT");
    assertThat(attempt.getLastError()).isNull();
    assertThat(attempt.getSentAt()).isNotNull();
  }
}
