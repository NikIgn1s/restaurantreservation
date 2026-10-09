package com.restaurant.reservation.presentation.mapper;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AddScheduleExceptionCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.presentation.request.ScheduleExceptionRequest;
import com.restaurant.reservation.presentation.request.SetScheduleRequest;
import com.restaurant.reservation.presentation.response.ScheduleExceptionResponse;
import com.restaurant.reservation.presentation.response.ScheduleResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class ScheduleApiMapper {

    public SetWeeklyScheduleCommand toCommand(Actor actor, UUID resourceId, DayOfWeek day, SetScheduleRequest request) {
        return new SetWeeklyScheduleCommand(actor, resourceId, day, request.openTime(), request.closeTime(),
                request.slotMinutes());
    }

    public AddScheduleExceptionCommand toCommand(Actor actor, UUID resourceId, LocalDate date,
                                                 ScheduleExceptionRequest request) {
        return new AddScheduleExceptionCommand(actor, resourceId, date, request.closed(), request.openTime(),
                request.closeTime());
    }

    public ScheduleResponse toResponse(Schedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getResourceId(), schedule.getDayOfWeek(),
                schedule.getOpenTime(), schedule.getCloseTime(), schedule.getSlotMinutes());
    }

    public ScheduleExceptionResponse toResponse(ScheduleException exception) {
        return new ScheduleExceptionResponse(exception.getId(), exception.getResourceId(), exception.getDate(),
                exception.isClosed(), exception.getOpenTime(), exception.getCloseTime());
    }
}
