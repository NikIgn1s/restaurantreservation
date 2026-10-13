package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.infrastructure.persistence.entity.BookingEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingEventJpaRepository extends JpaRepository<BookingEventEntity, UUID> {

    List<BookingEventEntity> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
