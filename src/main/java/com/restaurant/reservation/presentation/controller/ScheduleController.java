package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.usecase.AddScheduleExceptionUseCase;
import com.restaurant.reservation.application.usecase.GetResourceScheduleUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.presentation.mapper.ScheduleApiMapper;
import com.restaurant.reservation.presentation.request.ScheduleExceptionRequest;
import com.restaurant.reservation.presentation.request.SetScheduleRequest;
import com.restaurant.reservation.presentation.resolver.CurrentActor;
import com.restaurant.reservation.presentation.response.ScheduleExceptionResponse;
import com.restaurant.reservation.presentation.response.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources/{resourceId}/schedule")
@RequiredArgsConstructor
@Tag(name = "Schedule", description = "Weekly opening hours and special days of a resource")
public class ScheduleController {

    private final SetWeeklyScheduleUseCase setSchedule;
    private final GetResourceScheduleUseCase getSchedule;
    private final AddScheduleExceptionUseCase addException;
    private final ScheduleApiMapper mapper;

    @GetMapping
    @Operation(summary = "Weekly schedule of a resource")
    public List<ScheduleResponse> schedule(@PathVariable UUID resourceId) {
        return getSchedule.execute(resourceId).stream().map(mapper::toResponse).toList();
    }

    @PutMapping("/{dayOfWeek}")
    @Operation(summary = "Set opening hours for a weekday",
            description = "Creates the schedule for the day or replaces the existing one. Allowed for the venue owner and ADMIN")
    public ScheduleResponse setSchedule(@CurrentActor Actor actor, @PathVariable UUID resourceId,
                                        @Parameter(description = "MONDAY ... SUNDAY") @PathVariable DayOfWeek dayOfWeek,
                                        @Valid @RequestBody SetScheduleRequest request) {
        return mapper.toResponse(setSchedule.execute(mapper.toCommand(actor, resourceId, dayOfWeek, request)));
    }

    @PutMapping("/exceptions/{date}")
    @Operation(summary = "Close a day or set special hours for a date",
            description = "Overrides the weekly schedule for one date. Allowed for the venue owner and ADMIN")
    public ScheduleExceptionResponse addException(@CurrentActor Actor actor, @PathVariable UUID resourceId,
                                                  @Parameter(description = "yyyy-MM-dd", example = "2030-12-25")
                                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @Valid @RequestBody ScheduleExceptionRequest request) {
        return mapper.toResponse(addException.execute(mapper.toCommand(actor, resourceId, date, request)));
    }
}
