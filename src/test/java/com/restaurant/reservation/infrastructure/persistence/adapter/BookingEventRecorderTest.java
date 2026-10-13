package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.infrastructure.persistence.adapter.BookingEventRecorder.BookingSnapshot;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEventEntity;
import com.restaurant.reservation.infrastructure.persistence.repository.BookingEventJpaRepository;
import com.restaurant.reservation.infrastructure.security.SecurityActorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventRecorderTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private BookingEventJpaRepository events;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private BookingEventRecorder recorder;
    private Resource table;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        recorder = new BookingEventRecorder(events, objectMapper, new SecurityActorProvider(), Clock.fixed(NOW, ZoneOffset.UTC));
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        slot = new TimeSlot(Instant.parse("2030-01-07T12:00:00Z"), Instant.parse("2030-01-07T13:00:00Z"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Booking booking() {
        return Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
    }

    private void signInAs(Actor actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    }

    @SuppressWarnings("unchecked")
    private List<BookingEventEntity> recorded() {
        ArgumentCaptor<List<BookingEventEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).saveAll(captor.capture());
        return captor.getValue();
    }

    private JsonNode payload(BookingEventEntity entity) throws Exception {
        return objectMapper.readTree(entity.getPayload());
    }

    private static BookingSnapshot snapshotOf(Booking booking) {
        return new BookingSnapshot(booking.getStatus(), booking.isOverbooked(), booking.getReminderSentAt());
    }

    @Test
    void newBookingProducesACreatedEventWithItsDetails() throws Exception {
        Booking booking = booking();
        booking.assessNoShowRisk(0.7, 0.4);

        recorder.recordBookingSaved(null, booking);

        List<BookingEventEntity> saved = recorded();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo("CREATED");
        assertThat(saved.get(0).getBookingId()).isEqualTo(booking.getId());
        assertThat(saved.get(0).getCreatedAt()).isEqualTo(NOW);
        JsonNode body = payload(saved.get(0));
        assertThat(body.get("guests").asInt()).isEqualTo(2);
        assertThat(body.get("depositRequired").asBoolean()).isTrue();
        assertThat(body.get("noShowProbability").asDouble()).isEqualTo(0.7);
        assertThat(body.get("overbooked").asBoolean()).isFalse();
        assertThat(body.get("startAt").asText()).isEqualTo("2030-01-07T12:00:00Z");
        assertThat(body.get("userId").asText()).isEqualTo(booking.getUserId().toString());
    }

    @Test
    void standbyBookingAlsoProducesAStandbyPlacedEvent() {
        Booking booking = booking();
        booking.markOverbooked();

        recorder.recordBookingSaved(null, booking);

        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("CREATED", "STANDBY_PLACED");
    }

    @Test
    void eventsOfOneChangeKeepTheirOrderThroughIncreasingTimestamps() {
        Booking booking = booking();
        booking.markOverbooked();

        recorder.recordBookingSaved(null, booking);

        List<BookingEventEntity> saved = recorded();
        assertThat(saved.get(1).getCreatedAt()).isAfter(saved.get(0).getCreatedAt());
    }

    @Test
    void statusChangeProducesAnEventWithTheTransition() throws Exception {
        Booking booking = booking();
        BookingSnapshot before = snapshotOf(booking);
        booking.confirm();

        recorder.recordBookingSaved(before, booking);

        BookingEventEntity event = recorded().get(0);
        assertThat(event.getType()).isEqualTo("CONFIRMED");
        assertThat(payload(event).get("from").asText()).isEqualTo("PENDING");
        assertThat(payload(event).get("to").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void everyTerminalStatusHasItsOwnEventType() {
        for (BookingStatus target : List.of(BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
            Booking booking = booking();
            BookingSnapshot before = snapshotOf(booking);
            if (target == BookingStatus.CANCELLED) {
                booking.cancel();
            } else {
                booking.expire();
            }
            org.mockito.Mockito.clearInvocations(events);

            recorder.recordBookingSaved(before, booking);

            assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly(target.name());
        }
    }

    @Test
    void completedAndNoShowVisitsAreRecorded() {
        Booking completed = booking();
        completed.confirm();
        BookingSnapshot confirmed = snapshotOf(completed);
        completed.complete();

        recorder.recordBookingSaved(confirmed, completed);

        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("COMPLETED");
    }

    @Test
    void noShowIsRecorded() {
        Booking booking = booking();
        booking.confirm();
        BookingSnapshot confirmed = snapshotOf(booking);
        booking.markNoShow();

        recorder.recordBookingSaved(confirmed, booking);

        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("NO_SHOW");
    }

    @Test
    void standbyTakingOverIsRecorded() {
        Booking booking = booking();
        booking.markOverbooked();
        BookingSnapshot before = snapshotOf(booking);
        booking.promote();

        recorder.recordBookingSaved(before, booking);

        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("STANDBY_PROMOTED");
    }

    @Test
    void sentReminderIsRecordedOnce() {
        Booking booking = booking();
        booking.confirm();
        BookingSnapshot before = snapshotOf(booking);
        booking.markReminderSent(NOW);

        recorder.recordBookingSaved(before, booking);

        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("REMINDER_SENT");
    }

    @Test
    void saveWithoutAnyChangeProducesNoEvents() {
        Booking booking = booking();

        recorder.recordBookingSaved(snapshotOf(booking), booking);

        verify(events, never()).saveAll(anyList());
    }

    @Test
    void changeMadeByASignedInUserNamesTheUser() throws Exception {
        Actor owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        signInAs(owner);
        Booking booking = booking();
        BookingSnapshot before = snapshotOf(booking);
        booking.cancel();

        recorder.recordBookingSaved(before, booking);

        JsonNode actor = payload(recorded().get(0)).get("actor");
        assertThat(actor.get("type").asText()).isEqualTo("user");
        assertThat(actor.get("id").asText()).isEqualTo(owner.id().toString());
        assertThat(actor.get("role").asText()).isEqualTo("VENUE_OWNER");
    }

    @Test
    void changeWithoutASignedInUserIsAttributedToTheSystem() throws Exception {
        Booking booking = booking();
        BookingSnapshot before = snapshotOf(booking);
        booking.expire();

        recorder.recordBookingSaved(before, booking);

        assertThat(payload(recorded().get(0)).get("actor").get("type").asText()).isEqualTo("system");
    }

    @Test
    void actorIsTheFirstFieldOfEveryPayload() throws Exception {
        recorder.recordBookingSaved(null, booking());

        assertThat(payload(recorded().get(0)).fieldNames().next()).isEqualTo("actor");
    }

    @Test
    void startedPaymentIsRecordedForItsBooking() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Payment payment = Payment.start(bookingId, new BigDecimal("20"), "PLN", NOW);

        recorder.recordPaymentSaved(null, payment);

        BookingEventEntity event = recorded().get(0);
        assertThat(event.getType()).isEqualTo("PAYMENT_STARTED");
        assertThat(event.getBookingId()).isEqualTo(bookingId);
        assertThat(payload(event).get("amount").asText()).isEqualTo("20.00");
        assertThat(payload(event).get("currency").asText()).isEqualTo("PLN");
        assertThat(payload(event).has("reference")).isFalse();
    }

    @Test
    void paidPaymentCarriesTheGatewayReference() throws Exception {
        Payment payment = Payment.start(UUID.randomUUID(), new BigDecimal("20"), "PLN", NOW);
        payment.markPaid("ref-1");

        recorder.recordPaymentSaved(PaymentStatus.PENDING, payment);

        BookingEventEntity event = recorded().get(0);
        assertThat(event.getType()).isEqualTo("PAYMENT_PAID");
        assertThat(payload(event).get("reference").asText()).isEqualTo("ref-1");
    }

    @Test
    void failedAndRefundedPaymentsAreRecorded() {
        Payment failed = Payment.start(UUID.randomUUID(), new BigDecimal("20"), "PLN", NOW);
        failed.markFailed();
        recorder.recordPaymentSaved(PaymentStatus.PENDING, failed);
        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("PAYMENT_FAILED");

        org.mockito.Mockito.clearInvocations(events);
        Payment refunded = Payment.start(UUID.randomUUID(), new BigDecimal("20"), "PLN", NOW);
        refunded.markPaid("ref-2");
        refunded.markRefunded();
        recorder.recordPaymentSaved(PaymentStatus.PAID, refunded);
        assertThat(recorded()).extracting(BookingEventEntity::getType).containsExactly("PAYMENT_REFUNDED");
    }

    @Test
    void unchangedPaymentStatusProducesNoEvent() {
        Payment payment = Payment.start(UUID.randomUUID(), new BigDecimal("20"), "PLN", NOW);

        recorder.recordPaymentSaved(PaymentStatus.PENDING, payment);

        verify(events, never()).saveAll(anyList());
    }
}
