package com.restaurant.reservation.application.port;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(UUID userId, String key);

    void save(UUID userId, String key, String requestHash, UUID bookingId);
}
