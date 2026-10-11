package com.restaurant.reservation.infrastructure.payment;

import com.restaurant.reservation.application.port.ChargeRequest;
import com.restaurant.reservation.application.port.PaymentResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePaymentGatewayTest {

    private static ChargeRequest request(String email) {
        return new ChargeRequest(UUID.randomUUID(), new BigDecimal("20.00"), "PLN", email);
    }

    @Test
    void approvesByDefaultWithReferenceDerivedFromPaymentId() {
        ChargeRequest request = request("anna@example.com");

        PaymentResult result = new FakePaymentGateway("approve").charge(request);

        assertThat(result.approved()).isTrue();
        assertThat(result.reference()).isEqualTo("fake-" + request.paymentId());
        assertThat(result.declineReason()).isNull();
    }

    @Test
    void declinesPayersWhoseEmailStartsWithDecline() {
        PaymentResult result = new FakePaymentGateway("approve").charge(request("decline.me@example.com"));

        assertThat(result.approved()).isFalse();
        assertThat(result.reference()).isNull();
        assertThat(result.declineReason()).isNotBlank();
    }

    @Test
    void declineMarkerIsCaseInsensitive() {
        assertThat(new FakePaymentGateway("approve").charge(request("DECLINED@example.com")).approved()).isFalse();
    }

    @Test
    void declineAllModeRejectsEveryone() {
        assertThat(new FakePaymentGateway("decline_all").charge(request("anna@example.com")).approved()).isFalse();
    }

    @Test
    void outageModeThrows() {
        assertThatThrownBy(() -> new FakePaymentGateway("outage").charge(request("anna@example.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void modeIsCaseInsensitive() {
        assertThat(new FakePaymentGateway(" APPROVE ").charge(request("anna@example.com")).approved()).isTrue();
    }

    @Test
    void unknownModeIsRefusedAtStartup() {
        assertThatThrownBy(() -> new FakePaymentGateway("chaos")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundNeverFails() {
        assertThatCode(() -> new FakePaymentGateway("approve").refund("fake-1")).doesNotThrowAnyException();
    }
}
