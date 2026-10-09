package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfirmBookingUseCase {

    private final BookingRepository bookings;
    private final VenueAccessPolicy accessPolicy;

    @Transactional
    public Booking execute(BookingActionCommand command) {
        Booking booking = bookings.findById(command.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + command.bookingId() + " not found"));
        if (!canConfirm(booking, command)) {
            throw new ForbiddenOperationException("Booking cannot be confirmed by this user");
        }
        booking.confirm();
        return bookings.save(booking);
    }

    private boolean canConfirm(Booking booking, BookingActionCommand command) {
        if (accessPolicy.canManageResource(booking.getResourceId(), command.actor())) {
            return true;
        }
        return booking.isOwnedBy(command.actor().id()) && !booking.isDepositRequired();
    }
}
