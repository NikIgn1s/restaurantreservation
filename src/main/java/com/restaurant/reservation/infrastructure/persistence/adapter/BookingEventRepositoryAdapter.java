package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.model.BookingEvent;
import com.restaurant.reservation.domain.model.BookingEventType;
import com.restaurant.reservation.domain.repository.BookingEventRepository;
import com.restaurant.reservation.infrastructure.persistence.repository.BookingEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BookingEventRepositoryAdapter implements BookingEventRepository {

    private final BookingEventJpaRepository jpa;

    @Override
    @Transactional(readOnly = true)
    public List<BookingEvent> findByBookingId(UUID bookingId) {
        return jpa.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(entity -> new BookingEvent(entity.getId(), entity.getBookingId(),
                        BookingEventType.valueOf(entity.getType()), entity.getPayload(), entity.getCreatedAt()))
                .toList();
    }
}
