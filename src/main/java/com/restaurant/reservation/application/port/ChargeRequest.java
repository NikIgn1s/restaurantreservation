package com.restaurant.reservation.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargeRequest(UUID paymentId, BigDecimal amount, String currency, String payerEmail) {
}
