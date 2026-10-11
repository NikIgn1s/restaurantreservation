package com.restaurant.reservation.domain.model;

import com.restaurant.reservation.domain.exception.InvalidPaymentStateException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public class Payment {

    private final UUID id;
    private final UUID bookingId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private String externalReference;
    private final Instant createdAt;

    public Payment(UUID id, UUID bookingId, BigDecimal amount, String currency, PaymentStatus status,
                   String externalReference, Instant createdAt) {
        this.id = id;
        this.bookingId = bookingId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.externalReference = externalReference;
        this.createdAt = createdAt;
    }

    public static Payment start(UUID bookingId, BigDecimal amount, String currency, Instant now) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be a three letter code");
        }
        return new Payment(UUID.randomUUID(), bookingId, amount.setScale(2, RoundingMode.HALF_UP),
                currency.toUpperCase(), PaymentStatus.PENDING, null, now);
    }

    public void markPaid(String reference) {
        transitionTo(PaymentStatus.PAID);
        this.externalReference = reference;
    }

    public void markFailed() {
        transitionTo(PaymentStatus.FAILED);
    }

    public void markRefunded() {
        transitionTo(PaymentStatus.REFUNDED);
    }

    private void transitionTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentStateException(status, target);
        }
        this.status = target;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
