package com.restaurant.reservation.presentation.response;

public record DepositResponse(PaymentResponse payment, BookingResponse booking) {
}
