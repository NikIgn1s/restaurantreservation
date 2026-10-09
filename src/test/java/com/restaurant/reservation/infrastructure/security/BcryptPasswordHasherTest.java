package com.restaurant.reservation.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

    @Test
    void hashDiffersFromPassword() {
        assertThat(hasher.hash("password123")).isNotEqualTo("password123").startsWith("$2");
    }

    @Test
    void sameHashMatchesOriginalPassword() {
        String hash = hasher.hash("password123");

        assertThat(hasher.matches("password123", hash)).isTrue();
    }

    @Test
    void differentPasswordDoesNotMatch() {
        assertThat(hasher.matches("password124", hasher.hash("password123"))).isFalse();
    }

    @Test
    void sameInputGivesDifferentHashesBecauseOfSalt() {
        assertThat(hasher.hash("password123")).isNotEqualTo(hasher.hash("password123"));
    }

    @Test
    void nullInputsNeverMatch() {
        assertThat(hasher.matches(null, hasher.hash("password123"))).isFalse();
        assertThat(hasher.matches("password123", null)).isFalse();
    }

    @Test
    void garbageHashNeverMatches() {
        assertThat(hasher.matches("password123", "dev-only")).isFalse();
    }
}
