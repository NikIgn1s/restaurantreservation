package com.restaurant.reservation.application.port;

public interface PaymentGateway {

    PaymentResult charge(ChargeRequest request);

    void refund(String reference);
}
