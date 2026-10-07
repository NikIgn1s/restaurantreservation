package com.restaurant.reservation.domain.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    private final UUID resourceId = UUID.randomUUID();

    @Test
    void rejectsOpenTimeAfterCloseTime() {
        assertThatThrownBy(() -> Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(10, 0), 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveSlotLength() {
        assertThatThrownBy(() -> Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesOnlyToItsDayOfWeek() {
        Schedule schedule = Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), 60);

        assertThat(schedule.appliesTo(MONDAY)).isTrue();
        assertThat(schedule.appliesTo(MONDAY.plusDays(1))).isFalse();
    }

    @Test
    void generatesSlotsInVenueTimezone() {
        Schedule schedule = Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(13, 0), 60);

        List<TimeSlot> slots = schedule.slotsFor(MONDAY, WARSAW);

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).start()).isEqualTo(MONDAY.atTime(10, 0).atZone(WARSAW).toInstant());
        assertThat(slots.get(2).end()).isEqualTo(MONDAY.atTime(13, 0).atZone(WARSAW).toInstant());
    }

    @Test
    void dropsIncompleteTrailingSlot() {
        Schedule schedule = Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 30), 60);

        assertThat(schedule.slotsFor(MONDAY, WARSAW)).hasSize(2);
    }

    @Test
    void closedExceptionRemovesAllSlots() {
        Schedule schedule = Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), 60);
        ScheduleException holiday = ScheduleException.closedDay(resourceId, MONDAY);

        assertThat(schedule.slotsFor(holiday, WARSAW)).isEmpty();
    }

    @Test
    void specialHoursExceptionOverridesOpeningTime() {
        Schedule schedule = Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), 60);
        ScheduleException shortDay = ScheduleException.specialHours(resourceId, MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0));

        assertThat(schedule.slotsFor(shortDay, WARSAW)).hasSize(2);
    }

    @Test
    void specialHoursRequireValidInterval() {
        assertThatThrownBy(() -> ScheduleException.specialHours(resourceId, MONDAY, LocalTime.of(14, 0), LocalTime.of(12, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
