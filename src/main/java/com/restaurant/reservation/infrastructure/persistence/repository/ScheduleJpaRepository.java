package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.infrastructure.persistence.entity.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleJpaRepository extends JpaRepository<ScheduleEntity, UUID> {

    List<ScheduleEntity> findByResourceId(UUID resourceId);
}
