package se.cloudshop.accounting;

import java.time.LocalDate;

public record CreateStripePayoutRequest(
    LocalDate payoutDate,
    int grossAmount,
    int feeAmount,
    String reference
) {
}
