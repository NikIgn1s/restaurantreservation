package com.restaurant.reservation.infrastructure.scheduler;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "app.scheduler.enabled=true",
        "app.scheduler.initial-delay=PT0S",
        "app.scheduler.expiration-interval=PT0.2S",
        "app.scheduler.reminder-interval=PT0.2S",
        "app.scheduler.min-lock=PT1S"
})
class SchedulerLockIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private LockProvider lockProvider;
    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private BookingRepository bookings;

    private LockConfiguration lock(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
    }

    @Test
    void scheduledJobExpiresStaleBookingWithoutAnyManualCall() {
        Actor owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        Venue venue = createVenue.execute(
                new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        Resource table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        Instant start = Instant.now().plus(Duration.ofDays(2));
        Booking stale = bookings.save(Booking.create(table, insertUser(),
                new TimeSlot(start, start.plus(Duration.ofHours(1))), 2, Instant.now().minus(Duration.ofHours(2))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(bookings.findById(stale.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED));
    }

    @Test
    void jobsRecordTheirLocksInTheDatabase() {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer locks = jdbc.queryForObject(
                    "SELECT count(*) FROM shedlock WHERE name IN ('expireUnpaidBookings', 'sendBookingReminders')",
                    Integer.class);
            assertThat(locks).isEqualTo(2);
        });
    }

    @Test
    void lockCanBeHeldByOnlyOneNodeAtATime() {
        Optional<SimpleLock> first = lockProvider.lock(lock("exclusive-test"));
        Optional<SimpleLock> second = lockProvider.lock(lock("exclusive-test"));

        assertThat(first).isPresent();
        assertThat(second).isEmpty();

        first.orElseThrow().unlock();
        Optional<SimpleLock> third = lockProvider.lock(lock("exclusive-test"));
        assertThat(third).isPresent();
        third.orElseThrow().unlock();
    }

    @Test
    void differentLocksDoNotBlockEachOther() {
        Optional<SimpleLock> one = lockProvider.lock(lock("lock-one-" + UUID.randomUUID()));
        Optional<SimpleLock> two = lockProvider.lock(lock("lock-two-" + UUID.randomUUID()));

        assertThat(one).isPresent();
        assertThat(two).isPresent();
        one.orElseThrow().unlock();
        two.orElseThrow().unlock();
    }
}
