package se.cloudshop.order;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;

public record MarkInvoiceRefundRequest(
    LocalDate refundDate,
    @JsonDeserialize(using = WholeKronaAmountDeserializer.class) Integer refundAmount,
    String refundReference
) {
}
