package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import com.restaurant.reservation.infrastructure.persistence.entity.ScheduleEntity;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMappersTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void venueSurvivesRoundTrip() {
        VenueMapper mapper = new VenueMapper();
        Venue venue = Venue.create(UUID.randomUUID(), "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("Europe/Warsaw"));

        Venue restored = mapper.toDomain(mapper.toEntity(venue));

        assertThat(restored).usingRecursiveComparison().isEqualTo(venue);
    }

    @Test
    void resourceSurvivesRoundTrip() {
        ResourceMapper mapper = new ResourceMapper();
        Resource resource = Resource.create(UUID.randomUUID(), "Table 1", 4);

        Resource restored = mapper.toDomain(mapper.toEntity(resource));

        assertThat(restored).usingRecursiveComparison().isEqualTo(resource);
    }

    @Test
    void scheduleKeepsDayOfWeekAsIsoNumber() {
        ScheduleMapper mapper = new ScheduleMapper();
        Schedule schedule = Schedule.create(UUID.randomUUID(), DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(21, 0), 90);

        ScheduleEntity entity = mapper.toEntity(schedule);
        Schedule restored = mapper.toDomain(entity);

        assertThat(entity.getDayOfWeek()).isEqualTo((short) 7);
        assertThat(restored).usingRecursiveComparison().isEqualTo(schedule);
    }

    @Test
    void closedScheduleExceptionSurvivesRoundTrip() {
        ScheduleMapper mapper = new ScheduleMapper();
        ScheduleException exception = ScheduleException.closedDay(UUID.randomUUID(), LocalDate.of(2030, 12, 25));

        ScheduleException restored = mapper.toDomain(mapper.toEntity(exception));

        assertThat(restored).usingRecursiveComparison().isEqualTo(exception);
    }

    @Test
    void bookingSurvivesRoundTripIncludingRiskAndVersion() {
        BookingMapper mapper = new BookingMapper();
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(NOW.plus(Duration.ofHours(3)), NOW.plus(Duration.ofHours(5)));
        Booking booking = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
        booking.assessNoShowRisk(0.55, 0.4);
        booking.confirm();

        BookingEntity entity = mapper.toEntity(booking);
        Booking restored = mapper.toDomain(entity);

        assertThat(entity.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(entity.getStartAt()).isEqualTo(slot.start());
        assertThat(restored).usingRecursiveComparison().isEqualTo(booking);
    }

    @Test
    void bookingWithoutVersionIsReadAsVersionZero() {
        BookingMapper mapper = new BookingMapper();
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(NOW.plus(Duration.ofHours(3)), NOW.plus(Duration.ofHours(5)));
        BookingEntity entity = mapper.toEntity(Booking.create(table, UUID.randomUUID(), slot, 2, NOW));
        entity.setVersion(null);

        assertThat(mapper.toDomain(entity).getVersion()).isZero();
    }
}
