package com.restaurant.reservation.infrastructure.cache;

import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = "app.cache.slots-ttl=PT1S")
class RedisSlotCacheIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);

    @Autowired
    private SlotCache cache;

    private static TimeSlot hour(int start) {
        Instant base = Instant.parse("2030-01-07T00:00:00Z");
        return new TimeSlot(base.plus(Duration.ofHours(start)), base.plus(Duration.ofHours(start + 1)));
    }

    @Test
    void usesRedisImplementation() {
        assertThat(cache).isInstanceOf(RedisSlotCache.class);
    }

    @Test
    void missingEntryIsAMiss() {
        assertThat(cache.get(UUID.randomUUID(), DAY)).isEmpty();
    }

    @Test
    void storedSlotsComeBackIdentical() {
        UUID resourceId = UUID.randomUUID();
        List<TimeSlot> slots = List.of(hour(10), hour(11), hour(13));

        cache.put(resourceId, DAY, slots);

        assertThat(cache.get(resourceId, DAY)).contains(slots);
    }

    @Test
    void emptyListIsDifferentFromMiss() {
        UUID resourceId = UUID.randomUUID();

        cache.put(resourceId, DAY, List.of());

        assertThat(cache.get(resourceId, DAY)).contains(List.of());
    }

    @Test
    void datesAndResourcesAreIsolated() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        cache.put(first, DAY, List.of(hour(10)));
        cache.put(first, DAY.plusDays(1), List.of(hour(11)));
        cache.put(second, DAY, List.of(hour(12)));

        assertThat(cache.get(first, DAY)).contains(List.of(hour(10)));
        assertThat(cache.get(first, DAY.plusDays(1))).contains(List.of(hour(11)));
        assertThat(cache.get(second, DAY)).contains(List.of(hour(12)));
    }

    @Test
    void evictRemovesOnlyTheGivenDate() {
        UUID resourceId = UUID.randomUUID();
        cache.put(resourceId, DAY, List.of(hour(10)));
        cache.put(resourceId, DAY.plusDays(1), List.of(hour(11)));

        cache.evict(resourceId, DAY);

        assertThat(cache.get(resourceId, DAY)).isEmpty();
        assertThat(cache.get(resourceId, DAY.plusDays(1))).isPresent();
    }

    @Test
    void evictResourceRemovesEveryDateOfThatResourceOnly() {
        UUID resourceId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        cache.put(resourceId, DAY, List.of(hour(10)));
        cache.put(resourceId, DAY.plusDays(1), List.of(hour(11)));
        cache.put(other, DAY, List.of(hour(12)));

        cache.evictResource(resourceId);

        assertThat(cache.get(resourceId, DAY)).isEmpty();
        assertThat(cache.get(resourceId, DAY.plusDays(1))).isEmpty();
        assertThat(cache.get(other, DAY)).isPresent();
    }

    @Test
    void resourceCanBeCachedAgainAfterEviction() {
        UUID resourceId = UUID.randomUUID();
        cache.put(resourceId, DAY, List.of(hour(10)));
        cache.evictResource(resourceId);

        cache.put(resourceId, DAY, List.of(hour(14)));

        assertThat(cache.get(resourceId, DAY)).contains(List.of(hour(14)));
    }

    @Test
    void entriesExpireAfterTtl() {
        UUID resourceId = UUID.randomUUID();
        cache.put(resourceId, DAY, List.of(hour(10)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(cache.get(resourceId, DAY)).isEmpty());
    }

    @Test
    void corruptedEntryIsTreatedAsAMiss() {
        UUID resourceId = UUID.randomUUID();
        cache.put(resourceId, DAY, List.of(hour(10)));
        String key = redis.keys("slots:" + resourceId + ":*").iterator().next();
        redis.opsForValue().set(key, "{not json");

        assertThat(cache.get(resourceId, DAY)).isEmpty();
    }
}
