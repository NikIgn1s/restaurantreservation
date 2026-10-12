package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.exception.DomainException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
public class ExpireUnpaidBookingsUseCase {

    private final BookingRepository bookings;
    private final BookingExpirer expirer;
    private final Clock clock;
    private final Duration pendingTtl;

    public ExpireUnpaidBookingsUseCase(BookingRepository bookings, BookingExpirer expirer, Clock clock,
                                       @Value("${app.booking.pending-ttl:PT30M}") Duration pendingTtl) {
        this.bookings = bookings;
        this.expirer = expirer;
        this.clock = clock;
        this.pendingTtl = pendingTtl;
    }

    public int execute() {
        Instant threshold = clock.instant().minus(pendingTtl);
        int expired = 0;
        for (Booking booking : bookings.findPendingCreatedBefore(threshold)) {
            if (expire(booking)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expire(Booking booking) {
        try {
            expirer.expire(booking);
            return true;
        } catch (DomainException e) {
            log.info("Booking {} was not expired: {}", booking.getId(), e.getMessage());
            return false;
        }
    }
}
