package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> findForUpdate(@Param("id") UUID id);

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

    @Query("""
            select b from BookingEntity b
            where b.status = com.restaurant.reservation.domain.model.BookingStatus.CONFIRMED
              and b.reminderSentAt is null
              and b.startAt >= :from
              and b.startAt < :to
            order by b.startAt
            """)
    List<BookingEntity> findAwaitingReminder(@Param("from") Instant from, @Param("to") Instant to);

    long countByUserIdAndStatusIn(UUID userId, Collection<BookingStatus> statuses);

    long countByUserIdAndStatus(UUID userId, BookingStatus status);

    List<BookingEntity> findByStatusAndCreatedAtBefore(BookingStatus status, Instant threshold);

    List<BookingEntity> findByStatusAndStartAtGreaterThanEqualAndStartAtLessThan(BookingStatus status, Instant from, Instant to);
}
