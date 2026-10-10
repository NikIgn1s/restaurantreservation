package com.restaurant.reservation.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.domain.model.TimeSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisSlotCache implements SlotCache {

    private static final Duration VERSION_TTL = Duration.ofDays(1);
    private static final String DEFAULT_VERSION = "0";
    private static final TypeReference<List<CachedSlot>> SLOT_LIST = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisSlotCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                          @Value("${app.cache.slots-ttl:PT2M}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<List<TimeSlot>> get(UUID resourceId, LocalDate date) {
        try {
            String raw = redis.opsForValue().get(entryKey(resourceId, date, currentVersion(resourceId)));
            return raw == null ? Optional.empty() : Optional.of(deserialize(raw));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Slot cache read failed for resource {} on {}: {}", resourceId, date, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID resourceId, LocalDate date, List<TimeSlot> slots) {
        try {
            redis.opsForValue().set(entryKey(resourceId, date, currentVersion(resourceId)), serialize(slots), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Slot cache write failed for resource {} on {}: {}", resourceId, date, e.getMessage());
        }
    }

    @Override
    public void evict(UUID resourceId, LocalDate date) {
        try {
            redis.delete(entryKey(resourceId, date, currentVersion(resourceId)));
        } catch (RuntimeException e) {
            log.warn("Slot cache eviction failed for resource {} on {}: {}", resourceId, date, e.getMessage());
        }
    }

    @Override
    public void evictResource(UUID resourceId) {
        try {
            redis.opsForValue().increment(versionKey(resourceId));
            redis.expire(versionKey(resourceId), VERSION_TTL);
        } catch (RuntimeException e) {
            log.warn("Slot cache eviction failed for resource {}: {}", resourceId, e.getMessage());
        }
    }

    private String currentVersion(UUID resourceId) {
        String version = redis.opsForValue().get(versionKey(resourceId));
        return version == null ? DEFAULT_VERSION : version;
    }

    private static String versionKey(UUID resourceId) {
        return "slots:version:" + resourceId;
    }

    private static String entryKey(UUID resourceId, LocalDate date, String version) {
        return "slots:" + resourceId + ":v" + version + ":" + date;
    }

    private String serialize(List<TimeSlot> slots) throws JsonProcessingException {
        return objectMapper.writeValueAsString(slots.stream()
                .map(slot -> new CachedSlot(slot.start(), slot.end()))
                .toList());
    }

    private List<TimeSlot> deserialize(String raw) throws JsonProcessingException {
        return objectMapper.readValue(raw, SLOT_LIST).stream()
                .map(cached -> new TimeSlot(cached.startAt(), cached.endAt()))
                .toList();
    }

    private record CachedSlot(Instant startAt, Instant endAt) {
    }
}
