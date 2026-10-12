package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.policy.RiskPolicy;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOverbookableSlotsUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final Instant OPEN = Instant.parse("2030-01-07T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);

    @Mock
    private GetAvailableSlotsUseCase availableSlots;
    @Mock
    private BookingRepository bookings;

    private Resource table;
    private AvailableSlotsQuery query;
    private List<TimeSlot> scheduled;

    @BeforeEach
    void setUp() {
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        query = new AvailableSlotsQuery(table.getId(), DAY);
        scheduled = List.of(hour(0), hour(1), hour(2));
    }

    private static TimeSlot hour(int index) {
        return new TimeSlot(OPEN.plus(Duration.ofHours(index)), OPEN.plus(Duration.ofHours(index + 1)));
    }

    private Booking bookingAt(int index, double probability) {
        Booking booking = Booking.create(table, UUID.randomUUID(), hour(index), 2, NOW);
        booking.assessNoShowRisk(probability, 0.4);
        return booking;
    }

    private GetOverbookableSlotsUseCase useCase(boolean enabled) {
        return new GetOverbookableSlotsUseCase(availableSlots, bookings, new RiskPolicy(0.4, enabled, 0.5));
    }

    @Test
    void listsOnlySlotsHeldByAnUnpaidLikelyNoShow() {
        when(availableSlots.scheduledSlots(query)).thenReturn(scheduled);
        when(bookings.findActiveByResourceAndSlot(any(), any()))
                .thenReturn(List.of(bookingAt(0, 0.8), bookingAt(1, 0.2)));

        assertThat(useCase(true).execute(query)).containsExactly(hour(0));
    }

    @Test
    void doesNotListSlotsThatAlreadyHaveAStandby() {
        Booking standby = bookingAt(0, 0.9);
        standby.markOverbooked();
        when(availableSlots.scheduledSlots(query)).thenReturn(scheduled);
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of(bookingAt(0, 0.8), standby));

        assertThat(useCase(true).execute(query)).isEmpty();
    }

    @Test
    void freeSlotsAreNotOverbookable() {
        when(availableSlots.scheduledSlots(query)).thenReturn(scheduled);
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of());

        assertThat(useCase(true).execute(query)).isEmpty();
    }

    @Test
    void returnsNothingWhenTheDayHasNoSchedule() {
        when(availableSlots.scheduledSlots(query)).thenReturn(List.of());

        assertThat(useCase(true).execute(query)).isEmpty();
        verifyNoInteractions(bookings);
    }

    @Test
    void returnsNothingWhenOverbookingIsSwitchedOff() {
        when(availableSlots.scheduledSlots(query)).thenReturn(scheduled);

        assertThat(useCase(false).execute(query)).isEmpty();
        verifyNoInteractions(bookings);
    }

    @Test
    void unknownResourceFailsLikeTheSlotLookup() {
        when(availableSlots.scheduledSlots(query)).thenThrow(new NotFoundException("Resource not found"));

        assertThatThrownBy(() -> useCase(true).execute(query)).isInstanceOf(NotFoundException.class);
    }
}
