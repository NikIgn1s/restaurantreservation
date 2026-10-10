package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.ConcurrentBookingModificationException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class BookingConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final int THREADS = 8;
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final Instant CREATED = Instant.parse("2029-12-01T00:00:00Z");

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private CreateBookingUseCase createBooking;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private MockMvc mockMvc;

    private ExecutorService executor;
    private Actor owner;
    private Resource table;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(THREADS);
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        Venue venue = createVenue.execute(
                new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(14, 0), 60));
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static TimeSlot hour(int start) {
        return new TimeSlot(MONDAY.atTime(start, 0).toInstant(ZoneOffset.UTC), MONDAY.atTime(start + 1, 0).toInstant(ZoneOffset.UTC));
    }

    private record Outcome(Object value, Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }

    private List<Outcome> runSimultaneously(List<Callable<?>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return new Outcome(task.call(), null);
                } catch (Throwable error) {
                    return new Outcome(null, error);
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            try {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }
        return outcomes;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<Callable<?>> repositoryBookings(TimeSlot slot, int count) {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID userId = insertUser();
            tasks.add(() -> bookings.save(Booking.create(table, userId, slot, 2, CREATED)));
        }
        return tasks;
    }

    @Test
    void onlyOneRepositoryInsertWinsForTheSameSlot() throws Exception {
        List<Outcome> outcomes = runSimultaneously(repositoryBookings(hour(11), THREADS));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .hasSize(THREADS - 1)
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(SlotNotAvailableException.class));
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void differentSlotsDoNotBlockEachOther() throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int hour = 10; hour < 14; hour++) {
            tasks.addAll(repositoryBookings(hour(hour), 1));
        }

        List<Outcome> outcomes = runSimultaneously(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(count("bookings")).isEqualTo(4);
    }

    @Test
    void onlyOneBookingWinsThroughTheUseCase() throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Actor customer = new Actor(insertUser(), UserRole.CUSTOMER);
            tasks.add(() -> createBooking.execute(new CreateBookingCommand(customer, table.getId(), hour(11), 2, null)));
        }

        List<Outcome> outcomes = runSimultaneously(tasks);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(SlotNotAvailableException.class));
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void sameUserRetryingWithSameKeyCreatesOneBooking() throws Exception {
        Actor customer = new Actor(insertUser(), UserRole.CUSTOMER);
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> createBooking.execute(new CreateBookingCommand(customer, table.getId(), hour(12), 2, "same-key")));
        }

        List<Outcome> outcomes = runSimultaneously(tasks);

        assertThat(outcomes).filteredOn(Outcome::succeeded).isNotEmpty();
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
    }

    @Test
    void onlyOneHttpRequestWinsTheSlot() throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String token = bearer(insertUser(), UserRole.CUSTOMER);
            String body = """
                    {"resourceId": "%s", "startAt": "2030-01-07T11:00:00Z", "endAt": "2030-01-07T12:00:00Z", "guests": 2}
                    """.formatted(table.getId());
            tasks.add(() -> mockMvc.perform(post("/api/bookings")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus());
        }

        List<Outcome> outcomes = runSimultaneously(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes).filteredOn(outcome -> (int) outcome.value() == 201).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> (int) outcome.value() == 409).hasSize(THREADS - 1);
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void confirmAndCancelRacingOnSameBookingHaveOneWinner() throws Exception {
        Booking saved = bookings.save(Booking.create(table, insertUser(), hour(10), 2, CREATED));
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        List<Callable<?>> tasks = List.of(
                () -> {
                    Booking loaded = bookings.findById(saved.getId()).orElseThrow();
                    bothLoaded.await(10, TimeUnit.SECONDS);
                    loaded.confirm();
                    return bookings.save(loaded);
                },
                () -> {
                    Booking loaded = bookings.findById(saved.getId()).orElseThrow();
                    bothLoaded.await(10, TimeUnit.SECONDS);
                    loaded.cancel();
                    return bookings.save(loaded);
                });

        List<Outcome> outcomes = runSimultaneously(tasks);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.error()).isInstanceOf(ConcurrentBookingModificationException.class));
        Booking winner = (Booking) outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().value();
        Booking stored = bookings.findById(saved.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(winner.getStatus()).isIn(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
        assertThat(stored.getVersion()).isEqualTo(1);
    }

    @Test
    void twoSimultaneousCancellationsDoNotCorruptTheBooking() throws Exception {
        Booking saved = bookings.save(Booking.create(table, insertUser(), hour(10), 2, CREATED));
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        Callable<Booking> cancel = () -> {
            Booking loaded = bookings.findById(saved.getId()).orElseThrow();
            bothLoaded.await(10, TimeUnit.SECONDS);
            loaded.cancel();
            return bookings.save(loaded);
        };

        List<Outcome> outcomes = runSimultaneously(List.of(cancel, cancel));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.error()).isInstanceOf(ConcurrentBookingModificationException.class));
        Booking stored = bookings.findById(saved.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(stored.getVersion()).isEqualTo(1);
    }

    @Test
    void slotBecomesFreeAgainAfterTheLoserOfTheRaceIsRejected() throws Exception {
        runSimultaneously(repositoryBookings(hour(13), THREADS));
        Booking winner = bookings.findActiveByResourceAndSlot(table.getId(), hour(13)).get(0);
        winner.cancel();
        bookings.save(winner);

        Booking next = bookings.save(Booking.create(table, insertUser(), hour(13), 2, CREATED));

        assertThat(next.getStatus()).isEqualTo(BookingStatus.PENDING);
    }
}
