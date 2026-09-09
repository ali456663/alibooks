package se.cloudshop.order;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;

public record MarkInvoicePaidRequest(
    LocalDate paymentDate,
    @JsonDeserialize(using = WholeKronaAmountDeserializer.class) Integer paidAmount,
    String paymentReference
) {
}
