package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import com.restaurant.reservation.infrastructure.persistence.mapper.ScheduleMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.ScheduleExceptionJpaRepository;
import com.restaurant.reservation.infrastructure.persistence.repository.ScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryAdapter implements ScheduleRepository {

    private final ScheduleJpaRepository schedules;
    private final ScheduleExceptionJpaRepository exceptions;
    private final ScheduleMapper mapper;

    @Override
    @Transactional
    public Schedule save(Schedule schedule) {
        return mapper.toDomain(schedules.saveAndFlush(mapper.toEntity(schedule)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Schedule> findByResourceId(UUID resourceId) {
        return schedules.findByResourceId(resourceId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public ScheduleException saveException(ScheduleException exception) {
        return mapper.toDomain(exceptions.saveAndFlush(mapper.toEntity(exception)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduleException> findException(UUID resourceId, LocalDate date) {
        return exceptions.findByResourceIdAndDate(resourceId, date).map(mapper::toDomain);
    }
}
