package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AddScheduleExceptionCommand;
import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.application.usecase.AddScheduleExceptionUseCase;
import com.restaurant.reservation.application.usecase.CancelBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ExpireUnpaidBookingsUseCase;
import com.restaurant.reservation.application.usecase.GetAvailableSlotsUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotCacheInvalidationIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final Instant CREATED = Instant.parse("2029-12-01T00:00:00Z");

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private AddScheduleExceptionUseCase addException;
    @Autowired
    private GetAvailableSlotsUseCase getSlots;
    @Autowired
    private CreateBookingUseCase createBooking;
    @Autowired
    private CancelBookingUseCase cancelBooking;
    @Autowired
    private ConfirmBookingUseCase confirmBooking;
    @Autowired
    private ExpireUnpaidBookingsUseCase expireUnpaid;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private ResourceRepository resources;
    @Autowired
    private VenueRepository venues;
    @Autowired
    private SlotCache cache;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Actor owner;
    private Actor alice;
    private Actor bob;
    private Venue venue;
    private Resource table;

    @BeforeEach
    void prepare() {
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        alice = new Actor(insertUser(), UserRole.CUSTOMER);
        bob = new Actor(insertUser(), UserRole.CUSTOMER);
        venue = createVenue.execute(new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = newTable("Table 1");
    }

    private Resource newTable(String name) {
        Resource resource = createResource.execute(new CreateResourceCommand(owner, venue.getId(), name, 4));
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, resource.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(13, 0), 60));
        return resource;
    }

    private static TimeSlot hour(int start) {
        return new TimeSlot(MONDAY.atTime(start, 0).toInstant(ZoneOffset.UTC), MONDAY.atTime(start + 1, 0).toInstant(ZoneOffset.UTC));
    }

    private List<TimeSlot> slots(Resource resource) {
        return getSlots.execute(new AvailableSlotsQuery(resource.getId(), MONDAY));
    }

    private boolean cached(Resource resource) {
        return cache.get(resource.getId(), MONDAY).isPresent();
    }

    private Booking book(Actor actor, Resource resource, int startHour) {
        return createBooking.execute(new CreateBookingCommand(actor, resource.getId(), hour(startHour), 2, null)).booking();
    }

    private void insertBookingBehindTheCache(Resource resource, int startHour) {
        TimeSlot slot = hour(startHour);
        jdbc.update("INSERT INTO bookings (id, resource_id, user_id, start_at, end_at, guests, status, created_at) "
                        + "VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 2, 'CONFIRMED', now())",
                UUID.randomUUID(), resource.getId(), alice.id(), slot.start().toString(), slot.end().toString());
    }

    @Test
    void firstRequestPopulatesTheCache() {
        assertThat(cached(table)).isFalse();

        assertThat(slots(table)).hasSize(3);

        assertThat(cache.get(table.getId(), MONDAY)).hasValueSatisfying(stored -> assertThat(stored).hasSize(3));
    }

    @Test
    void repeatedRequestIsServedFromTheCache() {
        slots(table);
        insertBookingBehindTheCache(table, 11);

        assertThat(slots(table)).hasSize(3);
        assertThat(getSlots.executeFresh(new AvailableSlotsQuery(table.getId(), MONDAY))).hasSize(2);
    }

    @Test
    void newBookingEvictsTheCacheAfterCommit() {
        slots(table);
        assertThat(cached(table)).isTrue();

        book(alice, table, 11);

        assertThat(cached(table)).isFalse();
        assertThat(slots(table)).extracting(TimeSlot::start).containsExactly(hour(10).start(), hour(12).start());
    }

    @Test
    void cancellationFreesTheSlotAndEvictsTheCache() {
        Booking booking = book(alice, table, 11);
        assertThat(slots(table)).hasSize(2);

        cancelBooking.execute(new BookingActionCommand(alice, booking.getId()));

        assertThat(cached(table)).isFalse();
        assertThat(slots(table)).hasSize(3);
    }

    @Test
    void confirmationKeepsTheCacheBecauseOccupancyIsUnchanged() {
        Booking booking = book(alice, table, 11);
        slots(table);

        confirmBooking.execute(new BookingActionCommand(alice, booking.getId()));

        assertThat(cached(table)).isTrue();
        assertThat(slots(table)).hasSize(2);
    }

    @Test
    void expirationFreesTheSlotAndEvictsTheCache() {
        bookings.save(Booking.create(table, alice.id(), hour(11), 2, Instant.now().minus(Duration.ofHours(1))));
        assertThat(slots(table)).hasSize(2);

        assertThat(expireUnpaid.execute()).isEqualTo(1);

        assertThat(cached(table)).isFalse();
        assertThat(slots(table)).hasSize(3);
    }

    @Test
    void scheduleChangeEvictsTheCache() {
        assertThat(slots(table)).hasSize(3);

        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(12, 0), 60));

        assertThat(cached(table)).isFalse();
        assertThat(slots(table)).hasSize(2);
    }

    @Test
    void scheduleExceptionEvictsTheCache() {
        assertThat(slots(table)).hasSize(3);

        addException.execute(new AddScheduleExceptionCommand(owner, table.getId(), MONDAY, true, null, null));

        assertThat(cached(table)).isFalse();
        assertThat(slots(table)).isEmpty();
    }

    @Test
    void resourceDeactivationEvictsTheCache() {
        assertThat(slots(table)).hasSize(3);

        table.deactivate();
        resources.save(table);

        assertThat(cached(table)).isFalse();
        assertThat(slots(table)).isEmpty();
    }

    @Test
    void venueDeactivationEvictsEveryResourceOfTheVenue() {
        Resource second = newTable("Table 2");
        slots(table);
        slots(second);

        venue.deactivate();
        venues.save(venue);

        assertThat(cached(table)).isFalse();
        assertThat(cached(second)).isFalse();
        assertThat(slots(table)).isEmpty();
        assertThat(slots(second)).isEmpty();
    }

    @Test
    void bookingOneResourceDoesNotEvictAnother() {
        Resource second = newTable("Table 2");
        slots(table);
        slots(second);

        book(alice, table, 11);

        assertThat(cached(table)).isFalse();
        assertThat(cached(second)).isTrue();
    }

    @Test
    void rejectedBookingLeavesTheCacheUntouched() {
        book(alice, table, 11);
        slots(table);

        assertThatThrownBy(() -> book(bob, table, 11)).isInstanceOf(SlotNotAvailableException.class);

        assertThat(cached(table)).isTrue();
    }

    @Test
    void rolledBackTransactionDoesNotEvict() {
        slots(table);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            bookings.save(Booking.create(table, alice.id(), hour(11), 2, CREATED));
            throw new IllegalStateException("abort");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cached(table)).isTrue();
        assertThat(bookings.findActiveByResourceAndSlot(table.getId(), hour(11))).isEmpty();
    }

    @Test
    void evictionWaitsForTheCommit() {
        slots(table);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            bookings.save(Booking.create(table, alice.id(), hour(11), 2, CREATED));
            assertThat(cached(table)).isTrue();
        });

        assertThat(cached(table)).isFalse();
    }

    @Test
    void staleCacheNeverLetsTwoPeopleBookTheSameSlot() {
        slots(table);
        insertBookingBehindTheCache(table, 11);
        assertThat(slots(table)).hasSize(3);

        assertThatThrownBy(() -> book(bob, table, 11)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void staleCacheNeverBlocksAFreeSlot() {
        cache.put(table.getId(), MONDAY, List.of(hour(10)));

        Booking booking = book(bob, table, 12);

        assertThat(booking.getSlot()).isEqualTo(hour(12));
    }

    @Test
    void cachedSlotsInThePastAreNotReturned() {
        TimeSlot past = new TimeSlot(Instant.parse("2020-01-06T10:00:00Z"), Instant.parse("2020-01-06T11:00:00Z"));
        cache.put(table.getId(), MONDAY, List.of(past, hour(10)));

        assertThat(slots(table)).containsExactly(hour(10));
    }
}
