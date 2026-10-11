package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    List<Payment> findByBookingId(UUID bookingId);
}
