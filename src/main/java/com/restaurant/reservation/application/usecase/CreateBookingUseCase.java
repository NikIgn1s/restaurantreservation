package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.dto.BookingResult;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.port.IdempotencyRecord;
import com.restaurant.reservation.application.port.IdempotencyStore;
import com.restaurant.reservation.application.support.RequestFingerprint;
import com.restaurant.reservation.domain.exception.IdempotencyKeyConflictException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreateBookingUseCase {

    private static final int MAX_KEY_LENGTH = 128;

    private final ResourceRepository resources;
    private final VenueRepository venues;
    private final BookingRepository bookings;
    private final IdempotencyStore idempotencyStore;
    private final GetAvailableSlotsUseCase availableSlots;
    private final Clock clock;

    @Transactional
    public BookingResult execute(CreateBookingCommand command) {
        String fingerprint = RequestFingerprint.of(command.resourceId(), command.slot().start(),
                command.slot().end(), command.guests());

        if (command.hasIdempotencyKey()) {
            if (command.idempotencyKey().length() > MAX_KEY_LENGTH) {
                throw new InvalidBookingException("Idempotency key is too long");
            }
            Optional<BookingResult> replay = replay(command, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        Resource resource = resources.findById(command.resourceId())
                .orElseThrow(() -> new NotFoundException("Resource " + command.resourceId() + " not found"));
        Venue venue = venues.findById(resource.getVenueId())
                .orElseThrow(() -> new NotFoundException("Venue " + resource.getVenueId() + " not found"));
        if (!venue.isActive()) {
            throw new InvalidBookingException("Venue is not accepting bookings");
        }

        Booking booking = Booking.create(resource, command.actor().id(), command.slot(), command.guests(), clock.instant());
        ensureSlotIsOffered(command, venue);

        Booking saved = bookings.save(booking);
        if (command.hasIdempotencyKey()) {
            idempotencyStore.save(command.actor().id(), command.idempotencyKey(), fingerprint, saved.getId());
        }
        return new BookingResult(saved, false);
    }

    private Optional<BookingResult> replay(CreateBookingCommand command, String fingerprint) {
        return idempotencyStore.find(command.actor().id(), command.idempotencyKey())
                .map(record -> restore(command.idempotencyKey(), fingerprint, record));
    }

    private BookingResult restore(String key, String fingerprint, IdempotencyRecord record) {
        if (!record.requestHash().equals(fingerprint)) {
            throw new IdempotencyKeyConflictException(key);
        }
        Booking booking = bookings.findById(record.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + record.bookingId() + " not found"));
        return new BookingResult(booking, true);
    }

    private void ensureSlotIsOffered(CreateBookingCommand command, Venue venue) {
        var date = command.slot().start().atZone(venue.getTimezone()).toLocalDate();
        boolean offered = availableSlots.execute(new AvailableSlotsQuery(command.resourceId(), date))
                .contains(command.slot());
        if (!offered) {
            throw new SlotNotAvailableException("Requested slot is not available");
        }
    }
}
