package com.restaurant.reservation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.reservation.domain.model.TimeSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RedisSlotCacheResilienceTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);
    private static final TimeSlot SLOT = new TimeSlot(Instant.parse("2030-01-07T10:00:00Z"), Instant.parse("2030-01-07T11:00:00Z"));

    private LettuceConnectionFactory factory;
    private RedisSlotCache cache;

    @BeforeEach
    void connectToNowhere() {
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .build();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1), client);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        cache = new RedisSlotCache(template, new ObjectMapper().registerModule(new JavaTimeModule()), Duration.ofMinutes(1));
    }

    @AfterEach
    void disconnect() {
        factory.destroy();
    }

    @Test
    void readFailureIsReportedAsMiss() {
        assertThat(cache.get(UUID.randomUUID(), DAY)).isEmpty();
    }

    @Test
    void writeFailureDoesNotPropagate() {
        assertThatCode(() -> cache.put(UUID.randomUUID(), DAY, List.of(SLOT))).doesNotThrowAnyException();
    }

    @Test
    void evictionFailureDoesNotPropagate() {
        UUID resourceId = UUID.randomUUID();

        assertThatCode(() -> cache.evict(resourceId, DAY)).doesNotThrowAnyException();
        assertThatCode(() -> cache.evictResource(resourceId)).doesNotThrowAnyException();
    }
}
