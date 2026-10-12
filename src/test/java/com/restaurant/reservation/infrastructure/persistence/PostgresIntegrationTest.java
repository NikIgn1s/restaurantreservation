package com.restaurant.reservation.infrastructure.persistence;

import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest(properties = {"app.scheduler.enabled=false", "app.ml.enabled=false"})
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE TABLE idempotency_keys, booking_events, payments, bookings, "
                + "schedule_exceptions, schedules, resources, venues, users CASCADE");
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    protected UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, ?, ?, ?, ?)",
                id, id + "@test.com", "hash", "Test User", "CUSTOMER");
        return id;
    }

    protected String bearer(UUID userId, UserRole role) {
        User user = new User(userId, userId + "@test.com", "hash", "Test User", role, Instant.now());
        return "Bearer " + jwtService.issue(user).value();
    }
}
