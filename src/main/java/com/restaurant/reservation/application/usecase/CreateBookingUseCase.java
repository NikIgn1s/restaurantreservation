package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.dto.BookingResult;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.port.IdempotencyRecord;
import com.restaurant.reservation.application.policy.RiskPolicy;
import com.restaurant.reservation.application.port.IdempotencyStore;
import com.restaurant.reservation.application.port.NoShowPrediction;
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
import com.restaurant.reservation.domain.service.OverbookingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBookingUseCase {

    private static final int MAX_KEY_LENGTH = 128;

    private final ResourceRepository resources;
    private final VenueRepository venues;
    private final BookingRepository bookings;
    private final IdempotencyStore idempotencyStore;
    private final GetAvailableSlotsUseCase availableSlots;
    private final NoShowRiskAssessor riskAssessor;
    private final RiskPolicy riskPolicy;
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
        Optional<UUID> primaryToWaitFor = decideAccess(command, venue);
        assessRisk(booking, venue);
        primaryToWaitFor.ifPresent(primaryId -> queueBehind(booking, primaryId));

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

    private void assessRisk(Booking booking, Venue venue) {
        NoShowPrediction prediction = riskAssessor.assess(booking.getUserId(), venue, booking.getSlot(),
                booking.getGuests(), clock.instant());
        if (prediction.isAvailable()) {
            booking.assessNoShowRisk(prediction.probability(), riskPolicy.depositThreshold());
        }
    }

    private Optional<UUID> decideAccess(CreateBookingCommand command, Venue venue) {
        var date = command.slot().start().atZone(venue.getTimezone()).toLocalDate();
        var query = new AvailableSlotsQuery(command.resourceId(), date);
        if (availableSlots.executeFresh(query).contains(command.slot())) {
            return Optional.empty();
        }
        if (riskPolicy.overbookingEnabled() && availableSlots.scheduledSlots(query).contains(command.slot())) {
            List<Booking> active = bookings.findActiveByResourceAndSlot(command.resourceId(), command.slot());
            if (OverbookingPolicy.allows(active, riskPolicy.overbookingMinProbability())) {
                return Optional.of(active.get(0).getId());
            }
        }
        throw new SlotNotAvailableException("Requested slot is not available");
    }

    private void queueBehind(Booking booking, UUID primaryId) {
        bookings.lockById(primaryId).orElseThrow(() -> new SlotNotAvailableException("Requested slot is not available"));
        List<Booking> active = bookings.findActiveByResourceAndSlot(booking.getResourceId(), booking.getSlot());
        boolean stillAllowed = OverbookingPolicy.allows(active, riskPolicy.overbookingMinProbability())
                && active.get(0).getId().equals(primaryId);
        if (!stillAllowed) {
            throw new SlotNotAvailableException("Requested slot is not available");
        }
        booking.markOverbooked();
    }
}
