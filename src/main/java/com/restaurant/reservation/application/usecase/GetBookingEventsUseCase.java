package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingEvent;
import com.restaurant.reservation.domain.repository.BookingEventRepository;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetBookingEventsUseCase {

    private final BookingRepository bookings;
    private final BookingEventRepository events;
    private final VenueAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<BookingEvent> execute(BookingActionCommand command) {
        Booking booking = bookings.findById(command.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + command.bookingId() + " not found"));
        boolean owner = booking.isOwnedBy(command.actor().id());
        if (!owner && !accessPolicy.canManageResource(booking.getResourceId(), command.actor())) {
            throw new ForbiddenOperationException("Booking belongs to another user");
        }
        return events.findByBookingId(booking.getId());
    }
}
