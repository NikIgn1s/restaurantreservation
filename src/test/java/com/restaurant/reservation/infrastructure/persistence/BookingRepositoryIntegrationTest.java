package com.restaurant.reservation.infrastructure.persistence;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.restaurant.reservation.domain.exception.ConcurrentBookingModificationException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRepositoryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final Instant NOON = Instant.parse("2030-01-01T12:00:00Z");

    @Autowired
    private VenueRepository venues;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private BookingRepository bookings;

    private Resource table;
    private UUID userId;

    @BeforeEach
    void prepare() {
        userId = insertUser();
        Venue venue = venues.save(Venue.create(userId, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = resources.save(Resource.create(venue.getId(), "Table 1", 4));
    }

    private static TimeSlot slot(int fromMinutes, int toMinutes) {
        return new TimeSlot(NOON.plus(Duration.ofMinutes(fromMinutes)), NOON.plus(Duration.ofMinutes(toMinutes)));
    }

    private Booking newBooking(TimeSlot slot) {
        return Booking.create(table, userId, slot, 2, NOW);
    }

    @Test
    void savesAndLoadsBooking() {
        Booking booking = newBooking(slot(0, 60));
        booking.assessNoShowRisk(0.55, 0.4);

        Booking saved = bookings.save(booking);
        Booking loaded = bookings.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(loaded.getSlot()).isEqualTo(slot(0, 60));
        assertThat(loaded.getGuests()).isEqualTo(2);
        assertThat(loaded.isDepositRequired()).isTrue();
        assertThat(loaded.getNoShowProbability()).isEqualTo(0.55);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void newBookingStartsWithVersionZero() {
        assertThat(bookings.save(newBooking(slot(0, 60))).getVersion()).isZero();
    }

    @Test
    void versionGrowsOnEveryUpdate() {
        Booking saved = bookings.save(newBooking(slot(0, 60)));
        saved.confirm();
        Booking confirmed = bookings.save(saved);
        confirmed.complete();
        Booking completed = bookings.save(confirmed);

        assertThat(confirmed.getVersion()).isEqualTo(1);
        assertThat(completed.getVersion()).isEqualTo(2);
    }

    @Test
    void rejectsOverlappingActiveBooking() {
        bookings.save(newBooking(slot(0, 60)));

        assertThatThrownBy(() -> bookings.save(newBooking(slot(30, 90))))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void rejectsBookingInsideExistingOne() {
        bookings.save(newBooking(slot(0, 120)));

        assertThatThrownBy(() -> bookings.save(newBooking(slot(30, 60))))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void allowsAdjacentBookingsOnTheSameResource() {
        bookings.save(newBooking(slot(0, 60)));

        Booking next = bookings.save(newBooking(slot(60, 120)));

        assertThat(bookings.findById(next.getId())).isPresent();
    }

    @Test
    void allowsSameSlotOnAnotherResource() {
        Resource otherTable = resources.save(Resource.create(table.getVenueId(), "Table 2", 4));
        bookings.save(newBooking(slot(0, 60)));

        Booking other = bookings.save(Booking.create(otherTable, userId, slot(0, 60), 2, NOW));

        assertThat(bookings.findById(other.getId())).isPresent();
    }

    @Test
    void cancelledBookingReleasesTheSlot() {
        Booking first = bookings.save(newBooking(slot(0, 60)));
        first.cancel();
        bookings.save(first);

        Booking second = bookings.save(newBooking(slot(0, 60)));

        assertThat(bookings.findById(second.getId())).isPresent();
    }

    @Test
    void expiredBookingReleasesTheSlot() {
        Booking first = bookings.save(newBooking(slot(0, 60)));
        first.expire();
        bookings.save(first);

        assertThat(bookings.save(newBooking(slot(0, 60))).getId()).isNotNull();
    }

    @Test
    void rejectsStaleUpdateWithOptimisticLock() {
        Booking saved = bookings.save(newBooking(slot(0, 60)));
        Booking firstReader = bookings.findById(saved.getId()).orElseThrow();
        Booking secondReader = bookings.findById(saved.getId()).orElseThrow();

        firstReader.confirm();
        bookings.save(firstReader);
        secondReader.cancel();

        assertThatThrownBy(() -> bookings.save(secondReader))
                .isInstanceOf(ConcurrentBookingModificationException.class);
        assertThat(bookings.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void findsOnlyOverlappingActiveBookings() {
        Booking inside = bookings.save(newBooking(slot(0, 60)));
        bookings.save(newBooking(slot(120, 180)));
        Booking cancelled = bookings.save(newBooking(slot(240, 300)));
        cancelled.cancel();
        bookings.save(cancelled);

        assertThat(bookings.findActiveByResourceAndSlot(table.getId(), slot(30, 90)))
                .extracting(Booking::getId)
                .containsExactly(inside.getId());
        assertThat(bookings.findActiveByResourceAndSlot(table.getId(), slot(60, 120))).isEmpty();
        assertThat(bookings.findActiveByResourceAndSlot(table.getId(), slot(240, 300))).isEmpty();
    }

    @Test
    void findsBookingsOfUserNewestFirst() {
        bookings.save(newBooking(slot(0, 60)));
        bookings.save(newBooking(slot(120, 180)));
        UUID stranger = insertUser();
        bookings.save(Booking.create(table, stranger, slot(240, 300), 2, NOW));

        assertThat(bookings.findByUserId(userId))
                .extracting(booking -> booking.getSlot().start())
                .containsExactly(slot(120, 180).start(), slot(0, 60).start());
    }

    @Test
    void findsPendingBookingsCreatedBeforeThreshold() {
        Booking pending = bookings.save(newBooking(slot(0, 60)));
        Booking confirmed = bookings.save(newBooking(slot(120, 180)));
        confirmed.confirm();
        bookings.save(confirmed);

        assertThat(bookings.findPendingCreatedBefore(NOW.plus(Duration.ofMinutes(1))))
                .extracting(Booking::getId)
                .containsExactly(pending.getId());
        assertThat(bookings.findPendingCreatedBefore(NOW.minus(Duration.ofMinutes(1)))).isEmpty();
    }

    @Test
    void findsConfirmedBookingsStartingInsideWindow() {
        Booking inWindow = bookings.save(newBooking(slot(0, 60)));
        inWindow.confirm();
        bookings.save(inWindow);
        Booking later = bookings.save(newBooking(slot(300, 360)));
        later.confirm();
        bookings.save(later);
        bookings.save(newBooking(slot(120, 180)));

        assertThat(bookings.findConfirmedStartingBetween(NOON.minusSeconds(60), NOON.plus(Duration.ofMinutes(120))))
                .extracting(Booking::getId)
                .containsExactly(inWindow.getId());
    }
}
