package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.infrastructure.persistence.entity.ScheduleEntity;
import com.restaurant.reservation.infrastructure.persistence.entity.ScheduleExceptionEntity;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;

@Component
public class ScheduleMapper {

    public Schedule toDomain(ScheduleEntity entity) {
        return new Schedule(entity.getId(), entity.getResourceId(), DayOfWeek.of(entity.getDayOfWeek()),
                entity.getOpenTime(), entity.getCloseTime(), entity.getSlotMinutes());
    }

    public ScheduleEntity toEntity(Schedule schedule) {
        return new ScheduleEntity(schedule.getId(), schedule.getResourceId(), (short) schedule.getDayOfWeek().getValue(),
                schedule.getOpenTime(), schedule.getCloseTime(), schedule.getSlotMinutes());
    }

    public ScheduleException toDomain(ScheduleExceptionEntity entity) {
        return new ScheduleException(entity.getId(), entity.getResourceId(), entity.getDate(),
                entity.isClosed(), entity.getOpenTime(), entity.getCloseTime());
    }

    public ScheduleExceptionEntity toEntity(ScheduleException exception) {
        return new ScheduleExceptionEntity(exception.getId(), exception.getResourceId(), exception.getDate(),
                exception.isClosed(), exception.getOpenTime(), exception.getCloseTime());
    }
}
