package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingExpirer {

    private final BookingRepository bookings;
    private final OverbookingCoordinator overbooking;

    @Transactional
    public void expire(Booking booking) {
        booking.expire();
        overbooking.onSlotFreed(bookings.save(booking));
    }
}
