package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableSlotsUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Mock
    private ResourceRepository resources;
    @Mock
    private VenueRepository venues;
    @Mock
    private ScheduleRepository schedules;
    @Mock
    private BookingRepository bookings;
    @Mock
    private SlotCache cache;

    private GetAvailableSlotsUseCase useCase;
    private Venue venue;
    private Resource table;
    private Schedule monday;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new GetAvailableSlotsUseCase(resources, venues, schedules, bookings, cache, clock);
        venue = Venue.create(UUID.randomUUID(), "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC"));
        table = Resource.create(venue.getId(), "Table 1", 4);
        monday = Schedule.create(table.getId(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(13, 0), 60);
    }

    private void givenResourceAndVenue() {
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
    }

    private static Instant utc(int hour) {
        return MONDAY.atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    @Test
    void unknownResourceIsNotFound() {
        when(resources.findById(table.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void inactiveResourceHasNoSlots() {
        givenResourceAndVenue();
        table.deactivate();

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).isEmpty();
        verifyNoInteractions(schedules, bookings);
    }

    @Test
    void inactiveVenueHasNoSlots() {
        givenResourceAndVenue();
        venue.deactivate();

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).isEmpty();
        verifyNoInteractions(schedules, bookings);
    }

    @Test
    void dayWithoutScheduleHasNoSlots() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of());

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).isEmpty();
        verifyNoInteractions(bookings);
    }

    @Test
    void scheduleOfAnotherWeekdayIsIgnored() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY.plusDays(1)))).isEmpty();
    }

    @Test
    void returnsFreeSlotsAndLooksUpBookingsForWholeOpeningWindow() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY)).thenReturn(Optional.empty());
        Booking booked = Booking.create(table, UUID.randomUUID(), new TimeSlot(utc(11), utc(12)), 2, NOW);
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of(booked));

        List<TimeSlot> slots = useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        assertThat(slots).extracting(TimeSlot::start).containsExactly(utc(10), utc(12));
        ArgumentCaptor<TimeSlot> window = ArgumentCaptor.forClass(TimeSlot.class);
        verify(bookings).findActiveByResourceAndSlot(any(), window.capture());
        assertThat(window.getValue()).isEqualTo(new TimeSlot(utc(10), utc(13)));
    }

    @Test
    void closedDayExceptionLeavesNoSlots() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY))
                .thenReturn(Optional.of(ScheduleException.closedDay(table.getId(), MONDAY)));

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).isEmpty();
        verify(bookings, never()).findActiveByResourceAndSlot(any(), any());
    }

    @Test
    void specialHoursReplaceRegularOnes() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY)).thenReturn(Optional.of(
                ScheduleException.specialHours(table.getId(), MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0))));
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of());

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY)))
                .extracting(TimeSlot::start)
                .containsExactly(utc(14), utc(15));
    }

    @Test
    void servesSlotsFromCacheWithoutTouchingTheDatabase() {
        List<TimeSlot> cached = List.of(new TimeSlot(utc(10), utc(11)), new TimeSlot(utc(12), utc(13)));
        when(cache.get(table.getId(), MONDAY)).thenReturn(Optional.of(cached));

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).containsExactlyElementsOf(cached);

        verifyNoInteractions(resources, venues, schedules, bookings);
    }

    @Test
    void cachedSlotsThatAlreadyStartedAreFilteredOut() {
        TimeSlot past = new TimeSlot(Instant.parse("2029-12-31T10:00:00Z"), Instant.parse("2029-12-31T11:00:00Z"));
        TimeSlot future = new TimeSlot(utc(10), utc(11));
        when(cache.get(table.getId(), MONDAY)).thenReturn(Optional.of(List.of(past, future)));

        assertThat(useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).containsExactly(future);
    }

    @Test
    void cacheMissStoresTheComputedSlots() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY)).thenReturn(Optional.empty());
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of());

        useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        ArgumentCaptor<List<TimeSlot>> stored = ArgumentCaptor.forClass(List.class);
        verify(cache).put(eq(table.getId()), eq(MONDAY), stored.capture());
        assertThat(stored.getValue()).extracting(TimeSlot::start).containsExactly(utc(10), utc(11), utc(12));
    }

    @Test
    void cacheKeepsSlotsThatHaveStartedSoTheyAreFilteredPerRequest() {
        Clock lateClock = Clock.fixed(MONDAY.atTime(11, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        GetAvailableSlotsUseCase lateUseCase = new GetAvailableSlotsUseCase(resources, venues, schedules, bookings, cache, lateClock);
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY)).thenReturn(Optional.empty());
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of());

        List<TimeSlot> result = lateUseCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        ArgumentCaptor<List<TimeSlot>> stored = ArgumentCaptor.forClass(List.class);
        verify(cache).put(eq(table.getId()), eq(MONDAY), stored.capture());
        assertThat(stored.getValue()).hasSize(3);
        assertThat(result).extracting(TimeSlot::start).containsExactly(utc(12));
    }

    @Test
    void emptyResultIsCachedToo() {
        givenResourceAndVenue();
        table.deactivate();

        useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        verify(cache).put(table.getId(), MONDAY, List.of());
    }

    @Test
    void unknownResourceIsNeverCached() {
        when(resources.findById(table.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY)))
                .isInstanceOf(NotFoundException.class);

        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void freshLookupIgnoresAndDoesNotTouchTheCache() {
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY)).thenReturn(Optional.empty());
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of());

        assertThat(useCase.executeFresh(new AvailableSlotsQuery(table.getId(), MONDAY))).hasSize(3);

        verifyNoInteractions(cache);
    }

    @Test
    void usesVenueTimezoneForSlotBoundaries() {
        venue.update("Bistro", "Main st 1", ZoneId.of("Europe/Warsaw"));
        givenResourceAndVenue();
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(monday));
        when(schedules.findException(table.getId(), MONDAY)).thenReturn(Optional.empty());
        when(bookings.findActiveByResourceAndSlot(any(), any())).thenReturn(List.of());

        List<TimeSlot> slots = useCase.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        assertThat(slots.get(0).start()).isEqualTo(utc(9));
    }
}
