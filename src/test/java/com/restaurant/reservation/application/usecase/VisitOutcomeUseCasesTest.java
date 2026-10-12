package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitOutcomeUseCasesTest {

    private static final Instant CREATED = Instant.parse("2030-01-01T09:00:00Z");
    private static final Instant VISIT_START = Instant.parse("2030-01-07T19:00:00Z");

    @Mock
    private BookingRepository bookings;
    @Mock
    private VenueAccessPolicy accessPolicy;

    private Actor manager;
    private Booking booking;

    @BeforeEach
    void setUp() {
        manager = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        booking = Booking.create(table, UUID.randomUUID(), new TimeSlot(VISIT_START, VISIT_START.plusSeconds(3600)), 2, CREATED);
        booking.confirm();
    }

    private Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private BookingActionCommand command() {
        return new BookingActionCommand(manager, booking.getId());
    }

    private void givenBookingAndRights(boolean rights) {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(accessPolicy.canManageResource(booking.getResourceId(), manager)).thenReturn(rights);
    }

    @Test
    void managerCompletesAStartedVisit() {
        givenBookingAndRights(true);
        when(bookings.save(booking)).thenReturn(booking);

        Booking result = new CompleteBookingUseCase(bookings, accessPolicy, clockAt(VISIT_START.plusSeconds(60))).execute(command());

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void managerMarksNoShowForAStartedVisit() {
        givenBookingAndRights(true);
        when(bookings.save(booking)).thenReturn(booking);

        Booking result = new MarkNoShowUseCase(bookings, accessPolicy, clockAt(VISIT_START.plusSeconds(60))).execute(command());

        assertThat(result.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void outcomeCanBeRecordedExactlyWhenTheVisitStarts() {
        givenBookingAndRights(true);
        when(bookings.save(booking)).thenReturn(booking);

        assertThat(new MarkNoShowUseCase(bookings, accessPolicy, clockAt(VISIT_START)).execute(command()).getStatus())
                .isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void outcomeCannotBeRecordedBeforeTheVisit() {
        givenBookingAndRights(true);

        assertThatThrownBy(() -> new CompleteBookingUseCase(bookings, accessPolicy, clockAt(VISIT_START.minusSeconds(1))).execute(command()))
                .isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> new MarkNoShowUseCase(bookings, accessPolicy, clockAt(VISIT_START.minusSeconds(1))).execute(command()))
                .isInstanceOf(InvalidBookingException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void guestCannotRecordTheirOwnOutcome() {
        givenBookingAndRights(false);

        assertThatThrownBy(() -> new CompleteBookingUseCase(bookings, accessPolicy, clockAt(VISIT_START.plusSeconds(60))).execute(command()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> new MarkNoShowUseCase(bookings, accessPolicy, clockAt(VISIT_START.plusSeconds(60))).execute(command()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void pendingBookingCannotBeCompletedOrMarkedNoShow() {
        Resource table = Resource.create(UUID.randomUUID(), "Table 2", 4);
        booking = Booking.create(table, UUID.randomUUID(), new TimeSlot(VISIT_START, VISIT_START.plusSeconds(3600)), 2, CREATED);
        givenBookingAndRights(true);

        assertThatThrownBy(() -> new CompleteBookingUseCase(bookings, accessPolicy, clockAt(VISIT_START.plusSeconds(60))).execute(command()))
                .isInstanceOf(InvalidBookingStateException.class);
        assertThatThrownBy(() -> new MarkNoShowUseCase(bookings, accessPolicy, clockAt(VISIT_START.plusSeconds(60))).execute(command()))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CompleteBookingUseCase(bookings, accessPolicy, clockAt(VISIT_START)).execute(command()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> new MarkNoShowUseCase(bookings, accessPolicy, clockAt(VISIT_START)).execute(command()))
                .isInstanceOf(NotFoundException.class);
    }
}
