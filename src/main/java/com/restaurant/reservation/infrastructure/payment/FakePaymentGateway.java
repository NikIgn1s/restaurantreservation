package com.restaurant.reservation.infrastructure.payment;

import com.restaurant.reservation.application.port.ChargeRequest;
import com.restaurant.reservation.application.port.PaymentGateway;
import com.restaurant.reservation.application.port.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
public class FakePaymentGateway implements PaymentGateway {

    static final String DECLINED_EMAIL_PREFIX = "decline";

    private final Mode mode;

    public FakePaymentGateway(@Value("${app.payment.fake-mode:approve}") String mode) {
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        switch (mode) {
            case OUTAGE -> throw new IllegalStateException("Payment provider is unavailable");
            case DECLINE_ALL -> {
                return PaymentResult.declined("Card declined");
            }
            default -> {
                if (request.payerEmail() != null && request.payerEmail().toLowerCase(Locale.ROOT).startsWith(DECLINED_EMAIL_PREFIX)) {
                    return PaymentResult.declined("Insufficient funds");
                }
                log.info("Fake gateway charged {} {} for payment {}", request.amount(), request.currency(), request.paymentId());
                return PaymentResult.approved("fake-" + request.paymentId());
            }
        }
    }

    @Override
    public void refund(String reference) {
        log.info("Fake gateway refunded {}", reference);
    }

    enum Mode {
        APPROVE,
        DECLINE_ALL,
        OUTAGE
    }
}
