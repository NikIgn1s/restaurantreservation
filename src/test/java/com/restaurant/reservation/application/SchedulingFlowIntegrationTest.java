package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AddScheduleExceptionCommand;
import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.usecase.AddScheduleExceptionUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.GetAvailableSlotsUseCase;
import com.restaurant.reservation.application.usecase.GetResourceScheduleUseCase;
import com.restaurant.reservation.application.usecase.ListVenueResourcesUseCase;
import com.restaurant.reservation.application.usecase.ListVenuesUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulingFlowIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private ListVenuesUseCase listVenues;
    @Autowired
    private ListVenueResourcesUseCase listResources;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private GetResourceScheduleUseCase getSchedule;
    @Autowired
    private AddScheduleExceptionUseCase addException;
    @Autowired
    private GetAvailableSlotsUseCase availableSlots;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private ResourceRepository resourceRepository;

    private Actor owner;
    private Venue venue;
    private Resource table;

    @BeforeEach
    void prepare() {
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        venue = createVenue.execute(new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", WARSAW));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
    }

    private void openMondayFrom10To13() {
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(13, 0), 60));
    }

    private Instant warsaw(int hour) {
        return MONDAY.atTime(hour, 0).atZone(WARSAW).toInstant();
    }

    @Test
    void customerCannotCreateVenue() {
        Actor customer = new Actor(insertUser(), UserRole.CUSTOMER);

        assertThatThrownBy(() -> createVenue.execute(
                new CreateVenueCommand(customer, "Hack", VenueType.SPORT, "x", WARSAW)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void foreignOwnerCannotAddResource() {
        Actor stranger = new Actor(insertUser(), UserRole.VENUE_OWNER);

        assertThatThrownBy(() -> createResource.execute(
                new CreateResourceCommand(stranger, venue.getId(), "Table 2", 2)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void adminCanManageForeignVenue() {
        Actor admin = new Actor(insertUser(), UserRole.ADMIN);

        Resource created = createResource.execute(new CreateResourceCommand(admin, venue.getId(), "Table 2", 2));

        assertThat(created.getVenueId()).isEqualTo(venue.getId());
    }

    @Test
    void addingResourceToUnknownVenueFails() {
        assertThatThrownBy(() -> createResource.execute(
                new CreateResourceCommand(owner, UUID.randomUUID(), "Table 2", 2)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsActiveVenuesAndTheirActiveResources() {
        Resource hidden = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Hidden", 2));
        hidden.deactivate();
        resourceRepository.save(hidden);

        assertThat(listVenues.execute()).extracting(Venue::getName).containsExactly("Bistro");
        assertThat(listResources.execute(venue.getId())).extracting(Resource::getName).containsExactly("Table 1");
    }

    @Test
    void settingScheduleTwiceForTheSameDayUpdatesIt() {
        openMondayFrom10To13();
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(12, 0), LocalTime.of(20, 0), 30));

        List<Schedule> stored = getSchedule.execute(table.getId());

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getOpenTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(stored.get(0).getSlotMinutes()).isEqualTo(30);
    }

    @Test
    void foreignOwnerCannotChangeSchedule() {
        Actor stranger = new Actor(insertUser(), UserRole.VENUE_OWNER);

        assertThatThrownBy(() -> setSchedule.execute(new SetWeeklyScheduleCommand(stranger, table.getId(),
                DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(13, 0), 60)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void generatesSlotsInVenueTimezone() {
        openMondayFrom10To13();

        List<TimeSlot> slots = availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).start()).isEqualTo(warsaw(10));
        assertThat(slots.get(2).end()).isEqualTo(warsaw(13));
    }

    @Test
    void returnsNothingOnDayWithoutSchedule() {
        openMondayFrom10To13();

        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY.plusDays(1)))).isEmpty();
    }

    @Test
    void removesBookedSlots() {
        openMondayFrom10To13();
        TimeSlot booked = new TimeSlot(warsaw(11), warsaw(12));
        bookings.save(Booking.create(table, owner.id(), booked, 2, Instant.parse("2029-12-01T00:00:00Z")));

        List<TimeSlot> slots = availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        assertThat(slots).extracting(TimeSlot::start).containsExactly(warsaw(10), warsaw(12));
    }

    @Test
    void cancelledBookingFreesTheSlotAgain() {
        openMondayFrom10To13();
        Booking booking = bookings.save(Booking.create(table, owner.id(), new TimeSlot(warsaw(11), warsaw(12)), 2,
                Instant.parse("2029-12-01T00:00:00Z")));
        booking.cancel();
        bookings.save(booking);

        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).hasSize(3);
    }

    @Test
    void closedDayExceptionRemovesAllSlots() {
        openMondayFrom10To13();
        addException.execute(new AddScheduleExceptionCommand(owner, table.getId(), MONDAY, true, null, null));

        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).isEmpty();
    }

    @Test
    void specialHoursReplaceRegularOnes() {
        openMondayFrom10To13();
        addException.execute(new AddScheduleExceptionCommand(owner, table.getId(), MONDAY, false,
                LocalTime.of(12, 0), LocalTime.of(14, 0)));

        List<TimeSlot> slots = availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY));

        assertThat(slots).extracting(TimeSlot::start).containsExactly(warsaw(12), warsaw(13));
    }

    @Test
    void secondExceptionForSameDateReplacesFirst() {
        openMondayFrom10To13();
        addException.execute(new AddScheduleExceptionCommand(owner, table.getId(), MONDAY, true, null, null));
        addException.execute(new AddScheduleExceptionCommand(owner, table.getId(), MONDAY, false,
                LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).hasSize(1);
    }

    @Test
    void inactiveResourceHasNoSlots() {
        openMondayFrom10To13();
        table.deactivate();
        resourceRepository.save(table);

        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY))).isEmpty();
    }

    @Test
    void slotsOfUnknownResourceFail() {
        assertThatThrownBy(() -> availableSlots.execute(new AvailableSlotsQuery(UUID.randomUUID(), MONDAY)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pastSlotsAreNotOffered() {
        LocalDate past = LocalDate.of(2020, 1, 6);
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), past.getDayOfWeek(),
                LocalTime.of(10, 0), LocalTime.of(13, 0), 60));

        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), past))).isEmpty();
    }
}
