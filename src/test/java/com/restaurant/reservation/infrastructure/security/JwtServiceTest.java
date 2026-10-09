package com.restaurant.reservation.infrastructure.security;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.port.IssuedToken;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final User user = User.register("anna@example.com", "hash", "Anna", UserRole.VENUE_OWNER, NOW);

    private static JwtService service(String secret, String issuer, Instant now) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(1), issuer), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenCarriesUserAndRole() {
        JwtService service = service(SECRET, "reservation", NOW);

        IssuedToken token = service.issue(user);

        assertThat(token.value()).isNotBlank();
        assertThat(service.parse(token.value())).contains(new Actor(user.getId(), UserRole.VENUE_OWNER));
    }

    @Test
    void tokenExpiresAfterConfiguredTtl() {
        assertThat(service(SECRET, "reservation", NOW).issue(user).expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void tokenIsValidUntilItExpires() {
        String token = service(SECRET, "reservation", NOW).issue(user).value();

        assertThat(service(SECRET, "reservation", NOW.plus(Duration.ofMinutes(59))).parse(token)).isPresent();
        assertThat(service(SECRET, "reservation", NOW.plus(Duration.ofMinutes(61))).parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service("another-secret-another-secret-123456", "reservation", NOW).issue(user).value();

        assertThat(service(SECRET, "reservation", NOW).parse(token)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = service(SECRET, "someone-else", NOW).issue(user).value();

        assertThat(service(SECRET, "reservation", NOW).parse(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = service(SECRET, "reservation", NOW);
        String token = service.issue(user).value();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder().issuer("reservation").subject(user.getId().toString())
                .claim("role", "ADMIN").compact();

        assertThat(service(SECRET, "reservation", NOW).parse(unsigned)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        JwtService service = service(SECRET, "reservation", NOW);

        assertThat(service.parse("not-a-token")).isEqualTo(Optional.empty());
        assertThat(service.parse("")).isEmpty();
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> service("too-short", "reservation", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> service(null, "reservation", NOW)).isInstanceOf(IllegalStateException.class);
    }
}
