package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingResult;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.port.IdempotencyRecord;
import com.restaurant.reservation.application.port.IdempotencyStore;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.policy.RiskPolicy;
import com.restaurant.reservation.application.support.RequestFingerprint;
import com.restaurant.reservation.domain.exception.IdempotencyKeyConflictException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateBookingUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private ResourceRepository resources;
    @Mock
    private VenueRepository venues;
    @Mock
    private BookingRepository bookings;
    @Mock
    private IdempotencyStore idempotencyStore;
    @Mock
    private GetAvailableSlotsUseCase availableSlots;
    @Mock
    private NoShowRiskAssessor riskAssessor;

    private CreateBookingUseCase useCase;
    private Actor customer;
    private Venue venue;
    private Resource table;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new CreateBookingUseCase(resources, venues, bookings, idempotencyStore, availableSlots, riskAssessor,
                new RiskPolicy(0.4, true, 0.5), clock);
        customer = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        venue = Venue.create(UUID.randomUUID(), "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC"));
        table = Resource.create(venue.getId(), "Table 1", 4);
        slot = new TimeSlot(Instant.parse("2030-01-01T12:00:00Z"), Instant.parse("2030-01-01T13:00:00Z"));
    }

    private void givenResourceAndVenue() {
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
    }

    private void givenSlotIsOffered() {
        when(availableSlots.executeFresh(any())).thenReturn(List.of(slot));
        givenRiskIsUnavailable();
    }

    private void givenRiskIsUnavailable() {
        when(riskAssessor.assess(any(), any(), any(), anyInt(), any())).thenReturn(NoShowPrediction.unavailable());
    }

    private void givenRisk(double probability) {
        when(riskAssessor.assess(any(), any(), any(), anyInt(), any())).thenReturn(NoShowPrediction.of(probability, "v1"));
    }

    private void givenSaveReturnsArgument() {
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateBookingCommand command(String key) {
        return new CreateBookingCommand(customer, table.getId(), slot, 2, key);
    }

    private String fingerprint() {
        return RequestFingerprint.of(table.getId(), slot.start(), slot.end(), 2);
    }

    @Test
    void createsPendingBookingForOfferedSlot() {
        givenResourceAndVenue();
        givenSlotIsOffered();
        givenSaveReturnsArgument();

        BookingResult result = useCase.execute(command(null));

        assertThat(result.replayed()).isFalse();
        assertThat(result.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.booking().getUserId()).isEqualTo(customer.id());
        assertThat(result.booking().getSlot()).isEqualTo(slot);
        assertThat(result.booking().getCreatedAt()).isEqualTo(NOW);
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void storesIdempotencyKeyAfterCreation() {
        givenResourceAndVenue();
        givenSlotIsOffered();
        givenSaveReturnsArgument();
        when(idempotencyStore.find(customer.id(), "key-1")).thenReturn(Optional.empty());

        BookingResult result = useCase.execute(command("key-1"));

        verify(idempotencyStore).save(customer.id(), "key-1", fingerprint(), result.booking().getId());
    }

    @Test
    void replaysStoredBookingForSameKeyAndPayload() {
        Booking existing = Booking.create(table, customer.id(), slot, 2, NOW);
        when(idempotencyStore.find(customer.id(), "key-1"))
                .thenReturn(Optional.of(new IdempotencyRecord(fingerprint(), existing.getId())));
        when(bookings.findById(existing.getId())).thenReturn(Optional.of(existing));

        BookingResult result = useCase.execute(command("key-1"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.booking()).isSameAs(existing);
        verify(bookings, never()).save(any());
        verifyNoInteractions(resources, availableSlots);
    }

    @Test
    void rejectsKeyReusedWithDifferentPayload() {
        when(idempotencyStore.find(customer.id(), "key-1"))
                .thenReturn(Optional.of(new IdempotencyRecord("another-hash", UUID.randomUUID())));

        assertThatThrownBy(() -> useCase.execute(command("key-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void rejectsTooLongKey() {
        assertThatThrownBy(() -> useCase.execute(command("k".repeat(129))))
                .isInstanceOf(InvalidBookingException.class);
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void ignoresBlankKey() {
        givenResourceAndVenue();
        givenSlotIsOffered();
        givenSaveReturnsArgument();

        useCase.execute(command("   "));

        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void rejectsSlotThatIsNotOffered() {
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);
        verify(bookings, never()).save(any());
        verify(idempotencyStore, never()).save(any(), anyString(), anyString(), any());
    }

    @Test
    void rejectsUnknownResource() {
        when(resources.findById(table.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsInactiveVenue() {
        givenResourceAndVenue();
        venue.deactivate();

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(InvalidBookingException.class);
        verifyNoInteractions(availableSlots);
    }

    @Test
    void rejectsMoreGuestsThanCapacity() {
        givenResourceAndVenue();

        assertThatThrownBy(() -> useCase.execute(new CreateBookingCommand(customer, table.getId(), slot, 5, null)))
                .isInstanceOf(InvalidBookingException.class);
        verifyNoInteractions(availableSlots);
    }

    private void givenOfferedSlotWithRisk(double probability) {
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of(slot));
        givenRisk(probability);
        givenSaveReturnsArgument();
    }

    @Test
    void requiresDepositWhenProbabilityIsAboveTheThreshold() {
        givenOfferedSlotWithRisk(0.41);

        Booking booking = useCase.execute(command(null)).booking();

        assertThat(booking.isDepositRequired()).isTrue();
        assertThat(booking.getNoShowProbability()).isEqualTo(0.41);
    }

    @Test
    void doesNotRequireDepositWhenProbabilityEqualsTheThreshold() {
        givenOfferedSlotWithRisk(0.4);

        Booking booking = useCase.execute(command(null)).booking();

        assertThat(booking.isDepositRequired()).isFalse();
        assertThat(booking.getNoShowProbability()).isEqualTo(0.4);
    }

    @Test
    void doesNotRequireDepositForLowRisk() {
        givenOfferedSlotWithRisk(0.05);

        Booking booking = useCase.execute(command(null)).booking();

        assertThat(booking.isDepositRequired()).isFalse();
        assertThat(booking.getNoShowProbability()).isEqualTo(0.05);
    }

    @Test
    void usesTheConfiguredThreshold() {
        useCase = new CreateBookingUseCase(resources, venues, bookings, idempotencyStore, availableSlots, riskAssessor,
                new RiskPolicy(0.2, true, 0.5), Clock.fixed(NOW, ZoneOffset.UTC));
        givenOfferedSlotWithRisk(0.25);

        assertThat(useCase.execute(command(null)).booking().isDepositRequired()).isTrue();
    }

    @Test
    void leavesRiskUnknownAndSkipsDepositWhenPredictionIsUnavailable() {
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of(slot));
        givenRiskIsUnavailable();
        givenSaveReturnsArgument();

        Booking booking = useCase.execute(command(null)).booking();

        assertThat(booking.getNoShowProbability()).isNull();
        assertThat(booking.isDepositRequired()).isFalse();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void passesVenueSlotGuestsAndCurrentTimeToTheAssessor() {
        givenOfferedSlotWithRisk(0.1);

        useCase.execute(command(null));

        verify(riskAssessor).assess(customer.id(), venue, slot, 2, NOW);
    }

    @Test
    void doesNotAskForAPredictionWhenTheSlotIsRejected() {
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);

        verifyNoInteractions(riskAssessor);
    }

    private Booking riskyPrimaryInTheSlot() {
        Booking primary = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
        primary.assessNoShowRisk(0.8, 0.4);
        return primary;
    }

    private void givenSlotIsTakenButOnTheSchedule(Booking... active) {
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of());
        when(availableSlots.scheduledSlots(any())).thenReturn(List.of(slot));
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of(active));
    }

    @Test
    void placesTheBookingBehindAnUnpaidLikelyNoShow() {
        Booking primary = riskyPrimaryInTheSlot();
        givenSlotIsTakenButOnTheSchedule(primary);
        when(bookings.lockById(primary.getId())).thenReturn(Optional.of(primary));
        givenRiskIsUnavailable();
        givenSaveReturnsArgument();

        Booking standby = useCase.execute(command(null)).booking();

        assertThat(standby.isOverbooked()).isTrue();
        assertThat(standby.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookings).lockById(primary.getId());
    }

    @Test
    void standbyGetsItsOwnRiskAssessment() {
        Booking primary = riskyPrimaryInTheSlot();
        givenSlotIsTakenButOnTheSchedule(primary);
        when(bookings.lockById(primary.getId())).thenReturn(Optional.of(primary));
        givenRisk(0.6);
        givenSaveReturnsArgument();

        Booking standby = useCase.execute(command(null)).booking();

        assertThat(standby.isOverbooked()).isTrue();
        assertThat(standby.getNoShowProbability()).isEqualTo(0.6);
        assertThat(standby.isDepositRequired()).isTrue();
    }

    @Test
    void freeSlotIsBookedNormallyWithoutLockingAnything() {
        givenOfferedSlotWithRisk(0.1);

        Booking booking = useCase.execute(command(null)).booking();

        assertThat(booking.isOverbooked()).isFalse();
        verify(availableSlots, never()).scheduledSlots(any());
        verify(bookings, never()).lockById(any());
    }

    @Test
    void refusesWhenThePrimaryBookingIsReliable() {
        Booking reliable = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
        reliable.assessNoShowRisk(0.3, 0.4);
        givenSlotIsTakenButOnTheSchedule(reliable);

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);

        verify(bookings, never()).save(any());
        verifyNoInteractions(riskAssessor);
    }

    @Test
    void refusesWhenTheSlotAlreadyHasAStandby() {
        Booking primary = riskyPrimaryInTheSlot();
        Booking standby = riskyPrimaryInTheSlot();
        standby.markOverbooked();
        givenSlotIsTakenButOnTheSchedule(primary, standby);

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void refusesToOverbookASlotThatIsNotOnTheSchedule() {
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of());
        when(availableSlots.scheduledSlots(any())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);

        verify(bookings, never()).findActiveByResourceAndSlot(any(), any());
    }

    @Test
    void refusesWhenOverbookingIsSwitchedOff() {
        useCase = new CreateBookingUseCase(resources, venues, bookings, idempotencyStore, availableSlots, riskAssessor,
                new RiskPolicy(0.4, false, 0.5), Clock.fixed(NOW, ZoneOffset.UTC));
        givenResourceAndVenue();
        when(availableSlots.executeFresh(any())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);

        verify(availableSlots, never()).scheduledSlots(any());
    }

    @Test
    void usesTheConfiguredMinimumProbabilityForOverbooking() {
        useCase = new CreateBookingUseCase(resources, venues, bookings, idempotencyStore, availableSlots, riskAssessor,
                new RiskPolicy(0.4, true, 0.9), Clock.fixed(NOW, ZoneOffset.UTC));
        givenSlotIsTakenButOnTheSchedule(riskyPrimaryInTheSlot());

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void refusesWhenThePrimaryDisappearsBeforeItIsLocked() {
        Booking primary = riskyPrimaryInTheSlot();
        givenSlotIsTakenButOnTheSchedule(primary);
        when(bookings.lockById(primary.getId())).thenReturn(Optional.empty());
        givenRiskIsUnavailable();

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);

        verify(bookings, never()).save(any());
    }

    @Test
    void refusesWhenSomeoneElseTookTheStandbyPlaceMeanwhile() {
        Booking primary = riskyPrimaryInTheSlot();
        Booking rival = riskyPrimaryInTheSlot();
        rival.markOverbooked();
        givenSlotIsTakenButOnTheSchedule(primary);
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot))
                .thenReturn(List.of(primary))
                .thenReturn(List.of(primary, rival));
        when(bookings.lockById(primary.getId())).thenReturn(Optional.of(primary));
        givenRiskIsUnavailable();

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);

        verify(bookings, never()).save(any());
    }

    @Test
    void refusesWhenThePrimaryGotPaidMeanwhile() {
        Booking primary = riskyPrimaryInTheSlot();
        givenSlotIsTakenButOnTheSchedule(primary);
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot))
                .thenReturn(List.of(primary))
                .thenAnswer(invocation -> {
                    primary.confirm();
                    return List.of(primary);
                });
        when(bookings.lockById(primary.getId())).thenReturn(Optional.of(primary));
        givenRiskIsUnavailable();

        assertThatThrownBy(() -> useCase.execute(command(null))).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void rejectsSlotInThePast() {
        givenResourceAndVenue();
        TimeSlot past = new TimeSlot(Instant.parse("2029-12-31T10:00:00Z"), Instant.parse("2029-12-31T11:00:00Z"));

        assertThatThrownBy(() -> useCase.execute(new CreateBookingCommand(customer, table.getId(), past, 2, null)))
                .isInstanceOf(InvalidBookingException.class);
        verify(bookings, never()).save(any());
    }
}
