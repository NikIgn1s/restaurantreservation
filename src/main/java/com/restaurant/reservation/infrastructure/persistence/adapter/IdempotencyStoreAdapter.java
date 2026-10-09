package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.application.port.IdempotencyRecord;
import com.restaurant.reservation.application.port.IdempotencyStore;
import com.restaurant.reservation.infrastructure.persistence.entity.IdempotencyKeyEntity;
import com.restaurant.reservation.infrastructure.persistence.repository.IdempotencyKeyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IdempotencyStoreAdapter implements IdempotencyStore {

    private static final int CREATED = 201;

    private final IdempotencyKeyJpaRepository jpa;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(UUID userId, String key) {
        return jpa.findByUserIdAndIdempotencyKey(userId, key)
                .map(entity -> new IdempotencyRecord(entity.getRequestHash(), UUID.fromString(entity.getResponseBody())));
    }

    @Override
    @Transactional
    public void save(UUID userId, String key, String requestHash, UUID bookingId) {
        jpa.saveAndFlush(new IdempotencyKeyEntity(UUID.randomUUID(), userId, key, requestHash,
                CREATED, bookingId.toString(), clock.instant()));
    }
}
