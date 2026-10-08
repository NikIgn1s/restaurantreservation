package com.restaurant.reservation.infrastructure.persistence;

import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private VenueRepository venues;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private ScheduleRepository schedules;

    private UUID resourceId;

    @BeforeEach
    void createResource() {
        Venue venue = venues.save(Venue.create(insertUser(), "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        resourceId = resources.save(Resource.create(venue.getId(), "Table 1", 4)).getId();
    }

    @Test
    void savesAndLoadsSchedulesOfResource() {
        schedules.save(Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(22, 0), 60));
        schedules.save(Schedule.create(resourceId, DayOfWeek.SUNDAY, LocalTime.of(12, 0), LocalTime.of(20, 0), 90));

        assertThat(schedules.findByResourceId(resourceId))
                .extracting(Schedule::getDayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void keepsOpeningHoursAndSlotLength() {
        schedules.save(Schedule.create(resourceId, DayOfWeek.FRIDAY, LocalTime.of(9, 30), LocalTime.of(23, 0), 45));

        Schedule loaded = schedules.findByResourceId(resourceId).get(0);

        assertThat(loaded.getOpenTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(loaded.getCloseTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(loaded.getSlotMinutes()).isEqualTo(45);
    }

    @Test
    void rejectsSecondScheduleForTheSameDay() {
        schedules.save(Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(22, 0), 60));

        assertThatThrownBy(() -> schedules.save(
                Schedule.create(resourceId, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(20, 0), 60)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndFindsClosedDay() {
        LocalDate holiday = LocalDate.of(2030, 12, 25);
        schedules.saveException(ScheduleException.closedDay(resourceId, holiday));

        ScheduleException loaded = schedules.findException(resourceId, holiday).orElseThrow();

        assertThat(loaded.isClosed()).isTrue();
        assertThat(loaded.getOpenTime()).isNull();
    }

    @Test
    void savesAndFindsSpecialHours() {
        LocalDate day = LocalDate.of(2030, 12, 31);
        schedules.saveException(ScheduleException.specialHours(resourceId, day, LocalTime.of(12, 0), LocalTime.of(16, 0)));

        ScheduleException loaded = schedules.findException(resourceId, day).orElseThrow();

        assertThat(loaded.isClosed()).isFalse();
        assertThat(loaded.getOpenTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(loaded.getCloseTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void returnsEmptyWhenNoExceptionForDate() {
        assertThat(schedules.findException(resourceId, LocalDate.of(2030, 1, 1))).isEmpty();
    }
}
