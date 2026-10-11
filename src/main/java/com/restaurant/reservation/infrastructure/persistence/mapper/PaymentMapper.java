package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public Payment toDomain(PaymentEntity entity) {
        return new Payment(entity.getId(), entity.getBookingId(), entity.getAmount(), entity.getCurrency(),
                entity.getStatus(), entity.getExternalReference(), entity.getCreatedAt());
    }

    public PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(payment.getId(), payment.getBookingId(), payment.getAmount(), payment.getCurrency(),
                payment.getStatus(), payment.getExternalReference(), payment.getCreatedAt());
    }
}
