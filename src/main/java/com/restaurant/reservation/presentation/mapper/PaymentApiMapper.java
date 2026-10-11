package com.restaurant.reservation.presentation.mapper;

import com.restaurant.reservation.application.dto.DepositOutcome;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.presentation.response.DepositResponse;
import com.restaurant.reservation.presentation.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentApiMapper {

    private final BookingApiMapper bookingMapper;

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getAmount(), payment.getCurrency(),
                payment.getStatus(), payment.getExternalReference(), payment.getCreatedAt());
    }

    public DepositResponse toResponse(DepositOutcome outcome) {
        return new DepositResponse(toResponse(outcome.payment()), bookingMapper.toResponse(outcome.booking()));
    }
}
