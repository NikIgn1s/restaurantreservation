package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, UUID> {

    List<BookingEntity> findByUserIdOrderByStartAtDesc(UUID userId);

    @Query("""
            select b from BookingEntity b
            where b.resourceId = :resourceId
              and b.status in :statuses
              and b.startAt < :end
              and b.endAt > :start
            order by b.startAt
            """)
    List<BookingEntity> findOverlapping(@Param("resourceId") UUID resourceId,
                                        @Param("statuses") Collection<BookingStatus> statuses,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);

    List<BookingEntity> findByStatusAndCreatedAtBefore(BookingStatus status, Instant threshold);

    List<BookingEntity> findByStatusAndStartAtGreaterThanEqualAndStartAtLessThan(BookingStatus status, Instant from, Instant to);
}
