package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import com.restaurant.reservation.infrastructure.persistence.mapper.BookingMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.BookingJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BookingRepositoryAdapter implements BookingRepository {

    private final BookingJpaRepository jpa;
    private final BookingMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Booking save(Booking booking) {
        BookingEntity entity = mapper.toEntity(booking);
        if (jpa.existsById(entity.getId())) {
            return mapper.toDomain(jpa.saveAndFlush(entity));
        }
        entity.setVersion(null);
        entityManager.persist(entity);
        entityManager.flush();
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
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
    public List<Booking> findConfirmedStartingBetween(Instant from, Instant to) {
        return jpa.findByStatusAndStartAtGreaterThanEqualAndStartAtLessThan(BookingStatus.CONFIRMED, from, to).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
