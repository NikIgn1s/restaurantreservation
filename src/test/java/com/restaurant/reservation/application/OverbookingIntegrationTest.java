package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.application.usecase.CancelBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ExpireUnpaidBookingsUseCase;
import com.restaurant.reservation.application.usecase.GetAvailableSlotsUseCase;
import com.restaurant.reservation.application.usecase.GetOverbookableSlotsUseCase;
import com.restaurant.reservation.application.usecase.PayDepositUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.PaymentStatus;
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
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OverbookingIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final NoShowPrediction RISKY = NoShowPrediction.of(0.8, "v1");
    private static final NoShowPrediction RELIABLE = NoShowPrediction.of(0.2, "v1");

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private CreateBookingUseCase createBooking;
    @Autowired
    private CancelBookingUseCase cancelBooking;
    @Autowired
    private ConfirmBookingUseCase confirmBooking;
    @Autowired
    private PayDepositUseCase payDeposit;
    @Autowired
    private ExpireUnpaidBookingsUseCase expireUnpaid;
    @Autowired
    private GetAvailableSlotsUseCase freeSlots;
    @Autowired
    private GetOverbookableSlotsUseCase overbookableSlots;
    @Autowired
    private BookingRepository bookings;
    @MockBean
    private NoShowPredictor predictor;

    private ExecutorService executor;
    private Actor owner;
    private Venue venue;
    private Actor first;
    private Actor second;
    private Actor third;
    private Resource table;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(4);
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        first = new Actor(insertUser(), UserRole.CUSTOMER);
        second = new Actor(insertUser(), UserRole.CUSTOMER);
        third = new Actor(insertUser(), UserRole.CUSTOMER);
        venue = createVenue.execute(new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = newTable("Table 1");
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private Resource newTable(String name) {
        Resource resource = createResource.execute(new CreateResourceCommand(owner, venue.getId(), name, 4));
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, resource.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(16, 0), 60));
        return resource;
    }

    private static TimeSlot hour(int start) {
        return new TimeSlot(MONDAY.atTime(start, 0).toInstant(ZoneOffset.UTC), MONDAY.atTime(start + 1, 0).toInstant(ZoneOffset.UTC));
    }

    private Booking book(Actor actor, Resource resource, int startHour) {
        return createBooking.execute(new CreateBookingCommand(actor, resource.getId(), hour(startHour), 2, null)).booking();
    }

    private Booking book(Actor actor, int startHour) {
        return book(actor, table, startHour);
    }

    private Booking reload(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow();
    }

    private List<TimeSlot> overbookable(Resource resource) {
        return overbookableSlots.execute(new AvailableSlotsQuery(resource.getId(), MONDAY));
    }

    private List<Booking> activeIn(Resource resource, int startHour) {
        return bookings.findActiveByResourceAndSlot(resource.getId(), hour(startHour));
    }

    private void givenPredictions(NoShowPrediction... predictions) {
        var stubbing = when(predictor.predict(any()));
        for (NoShowPrediction prediction : predictions) {
            stubbing = stubbing.thenReturn(prediction);
        }
    }

    @Test
    void slotHeldByAnUnpaidLikelyNoShowIsListedAsOverbookable() {
        givenPredictions(RISKY);
        book(first, 11);

        assertThat(overbookable(table)).containsExactly(hour(11));
        assertThat(freeSlots.executeFresh(new AvailableSlotsQuery(table.getId(), MONDAY))).doesNotContain(hour(11));
    }

    @Test
    void secondGuestGetsAStandbyBookingBehindTheRiskyOne() {
        givenPredictions(RISKY, RELIABLE);
        Booking primary = book(first, 11);

        Booking standby = book(second, 11);

        assertThat(standby.isOverbooked()).isTrue();
        assertThat(reload(standby).isOverbooked()).isTrue();
        assertThat(reload(standby).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(reload(primary).isOverbooked()).isFalse();
        assertThat(activeIn(table, 11)).hasSize(2);
    }

    @Test
    void slotWithAStandbyIsNotOverbookableAnymore() {
        givenPredictions(RISKY, RELIABLE);
        book(first, 11);
        book(second, 11);

        assertThat(overbookable(table)).isEmpty();
    }

    @Test
    void onlyOneStandbyIsAllowedPerSlot() {
        givenPredictions(RISKY, RELIABLE, RELIABLE);
        book(first, 11);
        book(second, 11);

        assertThatThrownBy(() -> book(third, 11)).isInstanceOf(SlotNotAvailableException.class);

        assertThat(activeIn(table, 11)).hasSize(2);
    }

    @Test
    void reliablePrimaryCannotBeOverbooked() {
        givenPredictions(RELIABLE, RELIABLE);
        book(first, 11);

        assertThat(overbookable(table)).isEmpty();
        assertThatThrownBy(() -> book(second, 11)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void primaryWithPaidDepositCannotBeOverbooked() {
        givenPredictions(RISKY, RELIABLE);
        Booking primary = book(first, 11);
        payDeposit.execute(new BookingActionCommand(first, primary.getId()));

        assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(overbookable(table)).isEmpty();
        assertThatThrownBy(() -> book(second, 11)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void ordinaryFreeSlotsStayOrdinaryBookings() {
        givenPredictions(RISKY);

        Booking booking = book(first, 11);

        assertThat(booking.isOverbooked()).isFalse();
    }

    @Test
    void standbyTakesOverWhenThePrimaryGuestCancels() {
        givenPredictions(RISKY, RELIABLE);
        Booking primary = book(first, 11);
        Booking standby = book(second, 11);

        cancelBooking.execute(new BookingActionCommand(first, primary.getId()));

        assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reload(standby).isOverbooked()).isFalse();
        assertThat(reload(standby).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(activeIn(table, 11)).extracting(Booking::getId).containsExactly(standby.getId());
        assertThat(freeSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).doesNotContain(hour(11));
    }

    @Test
    void standbyTakesOverWhenThePrimaryExpires() {
        givenPredictions(RISKY, RELIABLE);
        Booking primary = book(first, 11);
        Booking standby = book(second, 11);
        jdbc.update("UPDATE bookings SET created_at = now() - interval '2 hours' WHERE id = ?", primary.getId());

        assertThat(expireUnpaid.execute()).isEqualTo(1);

        assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(reload(standby).isOverbooked()).isFalse();
        assertThat(reload(standby).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(freeSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).doesNotContain(hour(11));
    }

    @Test
    void standbyIsCancelledWhenThePrimaryPaysTheDeposit() {
        givenPredictions(RISKY, RELIABLE);
        Booking primary = book(first, 11);
        Booking standby = book(second, 11);

        var outcome = payDeposit.execute(new BookingActionCommand(first, primary.getId()));

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reload(standby).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(activeIn(table, 11)).extracting(Booking::getId).containsExactly(primary.getId());
    }

    @Test
    void standbyIsCancelledWhenTheVenueOwnerConfirmsThePrimary() {
        givenPredictions(RISKY, RELIABLE);
        Booking primary = book(first, 11);
        Booking standby = book(second, 11);

        confirmBooking.execute(new BookingActionCommand(owner, primary.getId()));

        assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reload(standby).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellingTheStandbyKeepsThePrimaryAndReopensTheSlotForAnotherStandby() {
        givenPredictions(RISKY, RELIABLE, RELIABLE);
        Booking primary = book(first, 11);
        Booking standby = book(second, 11);

        cancelBooking.execute(new BookingActionCommand(second, standby.getId()));

        assertThat(reload(standby).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(overbookable(table)).containsExactly(hour(11));
        assertThat(book(third, 11).isOverbooked()).isTrue();
    }

    @Test
    void standbyWithItsOwnHighRiskCanBeOverbookedNoFurther() {
        givenPredictions(RISKY, RISKY, RELIABLE);
        book(first, 11);
        Booking standby = book(second, 11);

        assertThat(standby.isDepositRequired()).isTrue();
        assertThatThrownBy(() -> book(third, 11)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void otherSlotsOfTheSameTableAreUnaffected() {
        givenPredictions(RISKY, RELIABLE, RELIABLE);
        book(first, 11);
        book(second, 11);

        Booking other = book(third, 12);

        assertThat(other.isOverbooked()).isFalse();
        assertThat(freeSlots.executeFresh(new AvailableSlotsQuery(table.getId(), MONDAY))).doesNotContain(hour(11), hour(12));
    }

    @Test
    void payingTheDepositRacesSafelyWithJoiningTheQueue() throws Exception {
        for (int round = 0; round < 6; round++) {
            Resource raceTable = newTable("Race table " + round);
            givenPredictions(RISKY);
            Booking primary = book(first, raceTable, 11);
            givenPredictions(RELIABLE);
            Actor guest = new Actor(insertUser(), UserRole.CUSTOMER);

            List<Callable<Object>> tasks = List.of(
                    () -> payDeposit.execute(new BookingActionCommand(first, primary.getId())),
                    () -> book(guest, raceTable, 11));
            runTogether(tasks);

            assertThat(reload(primary).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(activeIn(raceTable, 11)).as("round %d", round)
                    .extracting(Booking::getId).containsExactly(primary.getId());
        }
    }

    @Test
    void onlyOneOfTwoSimultaneousGuestsGetsTheStandbyPlace() throws Exception {
        for (int round = 0; round < 4; round++) {
            Resource raceTable = newTable("Standby table " + round);
            givenPredictions(RISKY);
            book(first, raceTable, 11);
            givenPredictions(RELIABLE);
            Actor one = new Actor(insertUser(), UserRole.CUSTOMER);
            Actor other = new Actor(insertUser(), UserRole.CUSTOMER);

            List<Object> results = runTogether(List.of(
                    () -> book(one, raceTable, 11),
                    () -> book(other, raceTable, 11)));

            assertThat(results).filteredOn(Booking.class::isInstance).as("round %d", round).hasSize(1);
            assertThat(results).filteredOn(SlotNotAvailableException.class::isInstance).hasSize(1);
            assertThat(activeIn(raceTable, 11)).hasSize(2);
        }
    }

    private List<Object> runTogether(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return task.call();
                } catch (Throwable error) {
                    return error;
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
    }
}
