package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.AddScheduleExceptionCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.usecase.AddScheduleExceptionUseCase;
import com.restaurant.reservation.application.usecase.GetResourceScheduleUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.presentation.mapper.ScheduleApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.restaurant.reservation.presentation.TestAuth.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, ScheduleApiMapper.class})
class ScheduleControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESOURCE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SetWeeklyScheduleUseCase setSchedule;
    @MockBean
    private GetResourceScheduleUseCase getSchedule;
    @MockBean
    private AddScheduleExceptionUseCase addException;

    @Test
    void setsScheduleForWeekday() throws Exception {
        Schedule schedule = Schedule.create(RESOURCE_ID, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(22, 0), 60);
        when(setSchedule.execute(any())).thenReturn(schedule);

        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.openTime").value("10:00:00"))
                .andExpect(jsonPath("$.closeTime").value("22:00:00"))
                .andExpect(jsonPath("$.slotMinutes").value(60));

        ArgumentCaptor<SetWeeklyScheduleCommand> captor = ArgumentCaptor.forClass(SetWeeklyScheduleCommand.class);
        verify(setSchedule).execute(captor.capture());
        assertThat(captor.getValue().resourceId()).isEqualTo(RESOURCE_ID);
        assertThat(captor.getValue().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(captor.getValue().openTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(captor.getValue().actor().id()).isEqualTo(OWNER_ID);
    }

    @Test
    void requiresAuthenticationToChangeSchedule() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.openTime").exists())
                .andExpect(jsonPath("$.errors.closeTime").exists())
                .andExpect(jsonPath("$.errors.slotMinutes").exists());

        verify(setSchedule, never()).execute(any());
    }

    @Test
    void rejectsTooShortSlots() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slotMinutes").exists());
    }

    @Test
    void rejectsMalformedTime() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"morning\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownWeekday() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "FUNDAY")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidHoursFromDomainBecomeBadRequest() throws Exception {
        when(setSchedule.execute(any())).thenThrow(new IllegalArgumentException("Open time must be before close time"));

        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"22:00\", \"closeTime\": \"10:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Open time must be before close time"));
    }

    @Test
    void foreignOwnerGetsForbidden() throws Exception {
        when(setSchedule.execute(any())).thenThrow(new ForbiddenOperationException("Venue belongs to another owner"));

        mockMvc.perform(put("/api/resources/{id}/schedule/{day}", RESOURCE_ID, "MONDAY")
                        .with(asUser(UUID.randomUUID().toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsWeeklyScheduleWithoutAuthentication() throws Exception {
        when(getSchedule.execute(RESOURCE_ID)).thenReturn(List.of(
                Schedule.create(RESOURCE_ID, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(22, 0), 60),
                Schedule.create(RESOURCE_ID, DayOfWeek.FRIDAY, LocalTime.of(12, 0), LocalTime.of(23, 0), 90)));

        mockMvc.perform(get("/api/resources/{id}/schedule", RESOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$[1].slotMinutes").value(90));
    }

    @Test
    void closesDayForDate() throws Exception {
        LocalDate date = LocalDate.of(2030, 12, 25);
        when(addException.execute(any())).thenReturn(ScheduleException.closedDay(RESOURCE_ID, date));

        mockMvc.perform(put("/api/resources/{id}/schedule/exceptions/{date}", RESOURCE_ID, "2030-12-25")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2030-12-25"))
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.openTime").doesNotExist());

        ArgumentCaptor<AddScheduleExceptionCommand> captor = ArgumentCaptor.forClass(AddScheduleExceptionCommand.class);
        verify(addException).execute(captor.capture());
        assertThat(captor.getValue().date()).isEqualTo(date);
        assertThat(captor.getValue().closed()).isTrue();
    }

    @Test
    void setsSpecialHoursForDate() throws Exception {
        LocalDate date = LocalDate.of(2030, 12, 31);
        when(addException.execute(any()))
                .thenReturn(ScheduleException.specialHours(RESOURCE_ID, date, LocalTime.of(12, 0), LocalTime.of(16, 0)));

        mockMvc.perform(put("/api/resources/{id}/schedule/exceptions/{date}", RESOURCE_ID, "2030-12-31")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closed\": false, \"openTime\": \"12:00\", \"closeTime\": \"16:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.openTime").value("12:00:00"))
                .andExpect(jsonPath("$.closeTime").value("16:00:00"));
    }

    @Test
    void rejectsExceptionWithoutClosedFlag() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/exceptions/{date}", RESOURCE_ID, "2030-12-31")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.closed").exists());
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/schedule/exceptions/{date}", RESOURCE_ID, "31-12-2030")
                        .with(asUser(OWNER_ID.toString(), "VENUE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closed\": true}"))
                .andExpect(status().isBadRequest());
    }
}
