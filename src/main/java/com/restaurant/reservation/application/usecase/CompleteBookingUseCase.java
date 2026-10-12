package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CompleteBookingUseCase {

    private final BookingRepository bookings;
    private final VenueAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional
    public Booking execute(BookingActionCommand command) {
        Booking booking = bookings.findById(command.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + command.bookingId() + " not found"));
        if (!accessPolicy.canManageResource(booking.getResourceId(), command.actor())) {
            throw new ForbiddenOperationException("Only the venue owner can record the outcome of a visit");
        }
        if (clock.instant().isBefore(booking.getSlot().start())) {
            throw new InvalidBookingException("The visit has not started yet");
        }
        booking.complete();
        return bookings.save(booking);
    }
}
