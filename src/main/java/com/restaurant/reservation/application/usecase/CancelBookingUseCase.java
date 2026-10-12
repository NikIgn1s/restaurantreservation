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
public class CancelBookingUseCase {

    private final BookingRepository bookings;
    private final VenueAccessPolicy accessPolicy;
    private final OverbookingCoordinator overbooking;

    @Transactional
    public Booking execute(BookingActionCommand command) {
        Booking booking = bookings.findById(command.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + command.bookingId() + " not found"));
        boolean owner = booking.isOwnedBy(command.actor().id());
        if (!owner && !accessPolicy.canManageResource(booking.getResourceId(), command.actor())) {
            throw new ForbiddenOperationException("Booking belongs to another user");
        }
        booking.cancel();
        Booking saved = bookings.save(booking);
        overbooking.onSlotFreed(saved);
        return saved;
    }
}
