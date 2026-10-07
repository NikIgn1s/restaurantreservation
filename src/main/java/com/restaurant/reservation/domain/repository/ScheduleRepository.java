package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {

    Schedule save(Schedule schedule);

    List<Schedule> findByResourceId(UUID resourceId);

    ScheduleException saveException(ScheduleException exception);

    Optional<ScheduleException> findException(UUID resourceId, LocalDate date);
}
