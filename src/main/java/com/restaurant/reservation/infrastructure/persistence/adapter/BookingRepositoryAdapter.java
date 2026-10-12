package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.exception.ConcurrentBookingModificationException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserBookingHistory;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import com.restaurant.reservation.infrastructure.persistence.mapper.BookingMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.BookingJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import com.restaurant.reservation.application.event.ResourceSlotsChanged;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BookingRepositoryAdapter implements BookingRepository {

    private static final List<String> OVERLAP_CONSTRAINTS = List.of("ex_bookings_no_overlap", "ex_bookings_single_overbooked");
    private static final String DEADLOCK_DETECTED = "40P01";
    private static final String SERIALIZATION_FAILURE = "40001";

    private final BookingJpaRepository jpa;
    private final BookingMapper mapper;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public Booking save(Booking booking) {
        boolean exists = jpa.existsById(booking.getId());
        try {
            Booking saved = exists ? update(booking) : insert(booking);
            if (!exists || !saved.getStatus().occupiesSlot()) {
                events.publishEvent(new ResourceSlotsChanged(saved.getResourceId()));
            }
            return saved;
        } catch (RuntimeException e) {
            if (violatesOverlapConstraint(e) || (!exists && isLockConflict(e))) {
                throw new SlotNotAvailableException("The slot was taken by another booking");
            }
            if (isOptimisticLockFailure(e)) {
                throw new ConcurrentBookingModificationException(booking.getId());
            }
            throw e;
        }
    }

    private Booking update(Booking booking) {
        return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(booking)));
    }

    private Booking insert(Booking booking) {
        BookingEntity entity = mapper.toEntity(booking);
        entity.setVersion(null);
        entityManager.persist(entity);
        entityManager.flush();
        return mapper.toDomain(entity);
    }

    private static boolean violatesOverlapConstraint(Throwable error) {
        String message = NestedExceptionUtils.getMostSpecificCause(error).getMessage();
        return message != null && OVERLAP_CONSTRAINTS.stream().anyMatch(message::contains);
    }

    private static boolean isLockConflict(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConcurrencyFailureException) {
                return true;
            }
            if (current instanceof SQLException sql
                    && (DEADLOCK_DETECTED.equals(sql.getSQLState()) || SERIALIZATION_FAILURE.equals(sql.getSQLState()))) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static boolean isOptimisticLockFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof StaleObjectStateException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Booking> lockById(UUID id) {
        return jpa.findForUpdate(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findByUserId(UUID userId) {
        return jpa.findByUserIdOrderByStartAtDesc(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findActiveByResourceAndSlot(UUID resourceId, TimeSlot slot) {
        List<BookingStatus> occupying = Arrays.stream(BookingStatus.values())
                .filter(BookingStatus::occupiesSlot)
                .toList();
        return jpa.findOverlapping(resourceId, occupying, slot.start(), slot.end()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findPendingCreatedBefore(Instant threshold) {
        return jpa.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, threshold).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserBookingHistory historyOf(UUID userId) {
        int finished = (int) jpa.countByUserIdAndStatusIn(userId, List.of(BookingStatus.COMPLETED, BookingStatus.NO_SHOW));
        int noShows = (int) jpa.countByUserIdAndStatus(userId, BookingStatus.NO_SHOW);
        return new UserBookingHistory(finished, noShows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findConfirmedAwaitingReminder(Instant from, Instant to) {
        return jpa.findAwaitingReminder(from, to).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findConfirmedStartingBetween(Instant from, Instant to) {
        return jpa.findByStatusAndStartAtGreaterThanEqualAndStartAtLessThan(BookingStatus.CONFIRMED, from, to).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
