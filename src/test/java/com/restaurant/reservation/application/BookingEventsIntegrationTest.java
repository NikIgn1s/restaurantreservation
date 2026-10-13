package com.restaurant.reservation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.application.usecase.CancelBookingUseCase;
import com.restaurant.reservation.application.usecase.CompleteBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ExpireUnpaidBookingsUseCase;
import com.restaurant.reservation.application.usecase.MarkNoShowUseCase;
import com.restaurant.reservation.application.usecase.PayDepositUseCase;
import com.restaurant.reservation.application.usecase.SendBookingRemindersUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingEvent;
import com.restaurant.reservation.domain.model.BookingEventType;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingEventRepository;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BookingEventsIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final NoShowPrediction RISKY = NoShowPrediction.of(0.8, "v1");
    private static final NoShowPrediction RELIABLE = NoShowPrediction.of(0.1, "v1");

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private CreateBookingUseCase createBooking;
    @Autowired
    private ConfirmBookingUseCase confirmBooking;
    @Autowired
    private CancelBookingUseCase cancelBooking;
    @Autowired
    private CompleteBookingUseCase completeBooking;
    @Autowired
    private MarkNoShowUseCase markNoShow;
    @Autowired
    private PayDepositUseCase payDeposit;
    @Autowired
    private ExpireUnpaidBookingsUseCase expireUnpaid;
    @Autowired
    private SendBookingRemindersUseCase sendReminders;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private BookingEventRepository eventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private NoShowPredictor predictor;

    private Actor owner;
    private Actor guest;
    private Actor other;
    private Resource table;

    @BeforeEach
    void prepare() {
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        guest = new Actor(insertUser(), UserRole.CUSTOMER);
        other = new Actor(insertUser(), UserRole.CUSTOMER);
        Venue venue = createVenue.execute(new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(16, 0), 60));
        when(predictor.predict(any())).thenReturn(RELIABLE);
    }

    private static TimeSlot hour(int start) {
        return new TimeSlot(MONDAY.atTime(start, 0).toInstant(ZoneOffset.UTC), MONDAY.atTime(start + 1, 0).toInstant(ZoneOffset.UTC));
    }

    private Booking book(Actor actor, int startHour) {
        return createBooking.execute(new CreateBookingCommand(actor, table.getId(), hour(startHour), 2, null)).booking();
    }

    private List<BookingEventType> typesOf(Booking booking) {
        return eventRepository.findByBookingId(booking.getId()).stream().map(BookingEvent::type).toList();
    }

    private JsonNode payloadOf(Booking booking, BookingEventType type) throws Exception {
        BookingEvent event = eventRepository.findByBookingId(booking.getId()).stream()
                .filter(candidate -> candidate.type() == type)
                .findFirst()
                .orElseThrow();
        return objectMapper.readTree(event.payload());
    }

    private int eventCount() {
        return jdbc.queryForObject("SELECT count(*) FROM booking_events", Integer.class);
    }

    private Booking visitInThePast(int hoursAgo) {
        Instant start = Instant.now().minus(Duration.ofHours(hoursAgo));
        Booking booking = Booking.create(table, guest.id(), new TimeSlot(start, start.plus(Duration.ofHours(1))), 2,
                Instant.parse("2019-01-01T00:00:00Z"));
        booking.confirm();
        return bookings.save(booking);
    }

    @Test
    void creatingABookingWritesACreatedEvent() throws Exception {
        Booking booking = book(guest, 11);

        assertThat(typesOf(booking)).containsExactly(BookingEventType.CREATED);
        JsonNode payload = payloadOf(booking, BookingEventType.CREATED);
        assertThat(payload.get("userId").asText()).isEqualTo(guest.id().toString());
        assertThat(payload.get("resourceId").asText()).isEqualTo(table.getId().toString());
        assertThat(payload.get("guests").asInt()).isEqualTo(2);
        assertThat(payload.get("noShowProbability").asDouble()).isEqualTo(0.1);
        assertThat(payload.get("depositRequired").asBoolean()).isFalse();
    }

    @Test
    void timestampsInThePayloadAreIsoStrings() throws Exception {
        JsonNode payload = payloadOf(book(guest, 11), BookingEventType.CREATED);

        assertThat(payload.get("startAt").asText()).isEqualTo("2030-01-07T11:00:00Z");
        assertThat(payload.get("endAt").asText()).isEqualTo("2030-01-07T12:00:00Z");
    }

    @Test
    void confirmationAndCancellationFormATimeline() throws Exception {
        Booking booking = book(guest, 11);
        confirmBooking.execute(new BookingActionCommand(guest, booking.getId()));
        cancelBooking.execute(new BookingActionCommand(guest, booking.getId()));

        assertThat(typesOf(booking)).containsExactly(BookingEventType.CREATED, BookingEventType.CONFIRMED, BookingEventType.CANCELLED);
        JsonNode cancelled = payloadOf(booking, BookingEventType.CANCELLED);
        assertThat(cancelled.get("from").asText()).isEqualTo("CONFIRMED");
        assertThat(cancelled.get("to").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void timelineIsReturnedInChronologicalOrder() {
        Booking booking = book(guest, 11);
        confirmBooking.execute(new BookingActionCommand(guest, booking.getId()));
        cancelBooking.execute(new BookingActionCommand(guest, booking.getId()));

        List<Instant> times = eventRepository.findByBookingId(booking.getId()).stream().map(BookingEvent::createdAt).toList();

        assertThat(times).isSorted();
    }

    @Test
    void visitOutcomeIsRecorded() {
        Booking attended = visitInThePast(2);
        Booking missed = visitInThePast(5);

        completeBooking.execute(new BookingActionCommand(owner, attended.getId()));
        markNoShow.execute(new BookingActionCommand(owner, missed.getId()));

        assertThat(typesOf(attended)).containsExactly(BookingEventType.CREATED, BookingEventType.COMPLETED);
        assertThat(typesOf(missed)).containsExactly(BookingEventType.CREATED, BookingEventType.NO_SHOW);
    }

    @Test
    void expiredBookingIsAttributedToTheSystem() throws Exception {
        Booking booking = book(guest, 11);
        jdbc.update("UPDATE bookings SET created_at = now() - interval '2 hours' WHERE id = ?", booking.getId());

        expireUnpaid.execute();

        assertThat(typesOf(booking)).containsExactly(BookingEventType.CREATED, BookingEventType.EXPIRED);
        assertThat(payloadOf(booking, BookingEventType.EXPIRED).get("actor").get("type").asText()).isEqualTo("system");
    }

    @Test
    void reminderIsRecordedWhenItIsSent() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        Booking soon = Booking.create(table, guest.id(), new TimeSlot(start, start.plus(Duration.ofHours(1))), 2,
                Instant.parse("2019-01-01T00:00:00Z"));
        soon.confirm();
        soon = bookings.save(soon);

        sendReminders.execute();

        assertThat(typesOf(soon)).containsExactly(BookingEventType.CREATED, BookingEventType.REMINDER_SENT);
    }

    @Test
    void depositPaymentLeavesTheWholeStoryInOrder() throws Exception {
        when(predictor.predict(any())).thenReturn(RISKY);
        Booking booking = book(guest, 11);

        payDeposit.execute(new BookingActionCommand(guest, booking.getId()));

        assertThat(typesOf(booking)).containsExactly(BookingEventType.CREATED, BookingEventType.PAYMENT_STARTED,
                BookingEventType.PAYMENT_PAID, BookingEventType.CONFIRMED);
        JsonNode paid = payloadOf(booking, BookingEventType.PAYMENT_PAID);
        assertThat(paid.get("amount").asText()).isEqualTo("20.00");
        assertThat(paid.get("currency").asText()).isEqualTo("PLN");
        assertThat(paid.get("reference").asText()).startsWith("fake-");
    }

    @Test
    void declinedPaymentIsRecordedAndTheBookingStaysPending() {
        when(predictor.predict(any())).thenReturn(RISKY);
        Actor decliner = new Actor(insertDecliningUser(), UserRole.CUSTOMER);
        Booking booking = book(decliner, 11);

        payDeposit.execute(new BookingActionCommand(decliner, booking.getId()));

        assertThat(typesOf(booking)).containsExactly(BookingEventType.CREATED, BookingEventType.PAYMENT_STARTED,
                BookingEventType.PAYMENT_FAILED);
    }

    private UUID insertDecliningUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, 'decline.me@test.com', 'hash', 'D', 'CUSTOMER')", id);
        return id;
    }

    @Test
    void standbyLifecycleIsVisibleInBothTimelines() {
        when(predictor.predict(any())).thenReturn(RISKY, RELIABLE);
        Booking primary = book(guest, 11);
        Booking standby = book(other, 11);

        cancelBooking.execute(new BookingActionCommand(guest, primary.getId()));

        assertThat(typesOf(standby)).containsExactly(BookingEventType.CREATED, BookingEventType.STANDBY_PLACED,
                BookingEventType.STANDBY_PROMOTED);
        assertThat(typesOf(primary)).containsExactly(BookingEventType.CREATED, BookingEventType.CANCELLED);
    }

    @Test
    void standbyCancelledByThePrimaryPaymentIsRecorded() {
        when(predictor.predict(any())).thenReturn(RISKY, RELIABLE);
        Booking primary = book(guest, 11);
        Booking standby = book(other, 11);

        payDeposit.execute(new BookingActionCommand(guest, primary.getId()));

        assertThat(typesOf(standby)).containsExactly(BookingEventType.CREATED, BookingEventType.STANDBY_PLACED,
                BookingEventType.CANCELLED);
    }

    @Test
    void rejectedBookingLeavesNoTrace() {
        book(guest, 11);
        int before = eventCount();

        assertThatThrownBy(() -> book(other, 11)).isInstanceOf(SlotNotAvailableException.class);

        assertThat(eventCount()).isEqualTo(before);
    }

    @Test
    void idempotentReplayDoesNotDuplicateEvents() {
        CreateBookingCommand command = new CreateBookingCommand(guest, table.getId(), hour(11), 2, "key-1");
        Booking first = createBooking.execute(command).booking();

        createBooking.execute(command);

        assertThat(typesOf(first)).containsExactly(BookingEventType.CREATED);
    }

    @Test
    void rolledBackChangeLeavesNoEvents() {
        Booking booking = book(guest, 11);
        int before = eventCount();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Booking loaded = bookings.findById(booking.getId()).orElseThrow();
            loaded.cancel();
            bookings.save(loaded);
            throw new IllegalStateException("abort");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(eventCount()).isEqualTo(before);
        assertThat(typesOf(booking)).containsExactly(BookingEventType.CREATED);
    }

    @Test
    void eventsBelongToTheirBookingOnly() {
        Booking first = book(guest, 11);
        Booking second = book(other, 12);
        cancelBooking.execute(new BookingActionCommand(guest, first.getId()));

        assertThat(typesOf(second)).containsExactly(BookingEventType.CREATED);
    }

    @Test
    void unknownBookingHasNoEvents() {
        assertThat(eventRepository.findByBookingId(UUID.randomUUID())).isEmpty();
    }
}
