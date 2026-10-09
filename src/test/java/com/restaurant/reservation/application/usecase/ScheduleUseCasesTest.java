package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AddScheduleExceptionCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleUseCasesTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);

    @Mock
    private VenueAccessPolicy accessPolicy;
    @Mock
    private ScheduleRepository schedules;
    @Mock
    private ResourceRepository resources;

    private Actor owner;
    private Resource table;

    @BeforeEach
    void setUp() {
        owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
    }

    private SetWeeklyScheduleCommand mondayCommand(int openHour, int closeHour, int slotMinutes) {
        return new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0), slotMinutes);
    }

    @Test
    void createsScheduleWhenDayHasNone() {
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenReturn(table);
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of());
        when(schedules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Schedule saved = new SetWeeklyScheduleUseCase(accessPolicy, schedules).execute(mondayCommand(10, 18, 60));

        assertThat(saved.getResourceId()).isEqualTo(table.getId());
        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.getSlotMinutes()).isEqualTo(60);
    }

    @Test
    void updatesExistingScheduleOfTheSameDay() {
        Schedule existing = Schedule.create(table.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        Schedule tuesday = Schedule.create(table.getId(), DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenReturn(table);
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(tuesday, existing));
        when(schedules.save(existing)).thenReturn(existing);

        Schedule saved = new SetWeeklyScheduleUseCase(accessPolicy, schedules).execute(mondayCommand(10, 20, 90));

        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.getCloseTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(saved.getSlotMinutes()).isEqualTo(90);
    }

    @Test
    void invalidHoursAreRejectedBeforeSaving() {
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenReturn(table);
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> new SetWeeklyScheduleUseCase(accessPolicy, schedules).execute(mondayCommand(18, 10, 60)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(schedules, never()).save(any());
    }

    @Test
    void foreignOwnerCannotChangeSchedule() {
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> new SetWeeklyScheduleUseCase(accessPolicy, schedules).execute(mondayCommand(10, 18, 60)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(schedules, never()).save(any());
    }

    @Test
    void createsClosedDayException() {
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenReturn(table);
        when(schedules.findException(table.getId(), DAY)).thenReturn(Optional.empty());
        when(schedules.saveException(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleException saved = new AddScheduleExceptionUseCase(accessPolicy, schedules)
                .execute(new AddScheduleExceptionCommand(owner, table.getId(), DAY, true, null, null));

        assertThat(saved.isClosed()).isTrue();
        assertThat(saved.getDate()).isEqualTo(DAY);
    }

    @Test
    void reusesIdOfExistingExceptionForTheSameDate() {
        ScheduleException existing = ScheduleException.closedDay(table.getId(), DAY);
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenReturn(table);
        when(schedules.findException(table.getId(), DAY)).thenReturn(Optional.of(existing));
        when(schedules.saveException(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleException saved = new AddScheduleExceptionUseCase(accessPolicy, schedules).execute(
                new AddScheduleExceptionCommand(owner, table.getId(), DAY, false, LocalTime.of(12, 0), LocalTime.of(15, 0)));

        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.isClosed()).isFalse();
        assertThat(saved.getOpenTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void specialHoursMustBeValid() {
        when(accessPolicy.loadManageableResource(table.getId(), owner)).thenReturn(table);
        when(schedules.findException(table.getId(), DAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AddScheduleExceptionUseCase(accessPolicy, schedules).execute(
                new AddScheduleExceptionCommand(owner, table.getId(), DAY, false, LocalTime.of(15, 0), LocalTime.of(12, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(schedules, never()).saveException(any());
    }

    @Test
    void returnsScheduleSortedByDayOfWeek() {
        Schedule friday = Schedule.create(table.getId(), DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        Schedule monday = Schedule.create(table.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(schedules.findByResourceId(table.getId())).thenReturn(List.of(friday, monday));

        assertThat(new GetResourceScheduleUseCase(resources, schedules).execute(table.getId()))
                .containsExactly(monday, friday);
    }

    @Test
    void scheduleOfUnknownResourceIsNotFound() {
        when(resources.findById(table.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetResourceScheduleUseCase(resources, schedules).execute(table.getId()))
                .isInstanceOf(NotFoundException.class);
    }
}
