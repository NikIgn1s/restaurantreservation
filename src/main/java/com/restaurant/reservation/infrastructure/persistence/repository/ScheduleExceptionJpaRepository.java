package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.infrastructure.persistence.entity.ScheduleExceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleExceptionJpaRepository extends JpaRepository<ScheduleExceptionEntity, UUID> {

    Optional<ScheduleExceptionEntity> findByResourceIdAndDate(UUID resourceId, LocalDate date);
}
