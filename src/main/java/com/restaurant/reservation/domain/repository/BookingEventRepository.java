package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.BookingEvent;

import java.util.List;
import java.util.UUID;

public interface BookingEventRepository {

    List<BookingEvent> findByBookingId(UUID bookingId);
}
