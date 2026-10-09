package com.restaurant.reservation.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Test
    void registersUserWithNormalizedEmail() {
        User user = User.register("  Anna@Example.COM ", "hash", "  Anna Kowalska ", UserRole.CUSTOMER, NOW);

        assertThat(user.getEmail()).isEqualTo("anna@example.com");
        assertThat(user.getFullName()).isEqualTo("Anna Kowalska");
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void rejectsEmailWithoutAtSign() {
        assertThatThrownBy(() -> User.register("not-an-email", "hash", "Anna", UserRole.CUSTOMER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankOrNullEmail() {
        assertThatThrownBy(() -> User.register("  ", "hash", "Anna", UserRole.CUSTOMER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.register(null, "hash", "Anna", UserRole.CUSTOMER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> User.register("a@b.c", "hash", " ", UserRole.CUSTOMER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPasswordHash() {
        assertThatThrownBy(() -> User.register("a@b.c", "", "Anna", UserRole.CUSTOMER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRole() {
        assertThatThrownBy(() -> User.register("a@b.c", "hash", "Anna", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesEmailForLookups() {
        assertThat(User.normalizeEmail("  MiXeD@Case.Org ")).isEqualTo("mixed@case.org");
        assertThat(User.normalizeEmail(null)).isEmpty();
    }
}
