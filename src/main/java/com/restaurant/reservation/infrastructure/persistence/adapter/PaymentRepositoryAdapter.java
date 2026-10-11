package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.exception.PaymentInProgressException;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.repository.PaymentRepository;
import com.restaurant.reservation.infrastructure.persistence.mapper.PaymentMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private static final String ACTIVE_PAYMENT_INDEX = "uq_payments_active_booking";

    private final PaymentJpaRepository jpa;
    private final PaymentMapper mapper;

    @Override
    @Transactional
    public Payment save(Payment payment) {
        try {
            return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(payment)));
        } catch (DataIntegrityViolationException e) {
            String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (message != null && message.contains(ACTIVE_PAYMENT_INDEX)) {
                throw new PaymentInProgressException(payment.getBookingId());
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findByBookingId(UUID bookingId) {
        return jpa.findByBookingIdOrderByCreatedAtDesc(bookingId).stream().map(mapper::toDomain).toList();
    }
}
