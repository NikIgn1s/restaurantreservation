package com.restaurant.reservation.domain.model;

import com.restaurant.reservation.domain.exception.InvalidPaymentStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    private Payment pending() {
        return Payment.start(UUID.randomUUID(), new BigDecimal("20"), "pln", NOW);
    }

    @Test
    void startsPendingWithNormalizedAmountAndCurrency() {
        Payment payment = pending();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("20.00");
        assertThat(payment.getAmount().scale()).isEqualTo(2);
        assertThat(payment.getCurrency()).isEqualTo("PLN");
        assertThat(payment.getExternalReference()).isNull();
        assertThat(payment.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void roundsAmountToCents() {
        assertThat(Payment.start(UUID.randomUUID(), new BigDecimal("19.999"), "EUR", NOW).getAmount())
                .isEqualByComparingTo("20.00");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.start(UUID.randomUUID(), BigDecimal.ZERO, "PLN", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.start(UUID.randomUUID(), new BigDecimal("-5"), "PLN", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.start(UUID.randomUUID(), null, "PLN", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> Payment.start(UUID.randomUUID(), BigDecimal.TEN, "ZLOTY", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.start(UUID.randomUUID(), BigDecimal.TEN, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingPaymentCanBePaidAndKeepsReference() {
        Payment payment = pending();

        payment.markPaid("ref-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getExternalReference()).isEqualTo("ref-1");
    }

    @Test
    void pendingPaymentCanFail() {
        Payment payment = pending();

        payment.markFailed();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void paidPaymentCanBeRefunded() {
        Payment payment = pending();
        payment.markPaid("ref-1");

        payment.markRefunded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void pendingPaymentCannotBeRefunded() {
        assertThatThrownBy(() -> pending().markRefunded()).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void failedPaymentCannotBePaid() {
        Payment payment = pending();
        payment.markFailed();

        assertThatThrownBy(() -> payment.markPaid("ref-1")).isInstanceOf(InvalidPaymentStateException.class);
        assertThat(payment.getExternalReference()).isNull();
    }

    @Test
    void paidPaymentCannotFailOrBePaidTwice() {
        Payment payment = pending();
        payment.markPaid("ref-1");

        assertThatThrownBy(payment::markFailed).isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> payment.markPaid("ref-2")).isInstanceOf(InvalidPaymentStateException.class);
        assertThat(payment.getExternalReference()).isEqualTo("ref-1");
    }

    @Test
    void refundedPaymentIsFinal() {
        Payment payment = pending();
        payment.markPaid("ref-1");
        payment.markRefunded();

        assertThatThrownBy(payment::markRefunded).isInstanceOf(InvalidPaymentStateException.class);
        assertThat(PaymentStatus.REFUNDED.allowedTransitions()).isEmpty();
    }

    @Test
    void onlyPendingAndPaidAreActive() {
        assertThat(PaymentStatus.PENDING.isActive()).isTrue();
        assertThat(PaymentStatus.PAID.isActive()).isTrue();
        assertThat(PaymentStatus.FAILED.isActive()).isFalse();
        assertThat(PaymentStatus.REFUNDED.isActive()).isFalse();
    }
}
