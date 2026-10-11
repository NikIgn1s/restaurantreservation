package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetBookingPaymentsUseCase {

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final VenueAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<Payment> execute(BookingActionCommand command) {
        Booking booking = bookings.findById(command.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + command.bookingId() + " not found"));
        boolean owner = booking.isOwnedBy(command.actor().id());
        if (!owner && !accessPolicy.canManageResource(booking.getResourceId(), command.actor())) {
            throw new ForbiddenOperationException("Booking belongs to another user");
        }
        return payments.findByBookingId(booking.getId());
    }
}
