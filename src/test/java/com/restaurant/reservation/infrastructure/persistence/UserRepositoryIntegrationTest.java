package com.restaurant.reservation.infrastructure.persistence;

import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Autowired
    private UserRepository users;

    private User user(String email) {
        return User.register(email, "hash", "Anna Kowalska", UserRole.CUSTOMER, NOW);
    }

    @Test
    void savesAndLoadsUserById() {
        User saved = users.save(user("anna@example.com"));

        User loaded = users.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getEmail()).isEqualTo("anna@example.com");
        assertThat(loaded.getPasswordHash()).isEqualTo("hash");
        assertThat(loaded.getFullName()).isEqualTo("Anna Kowalska");
        assertThat(loaded.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void findsUserByEmail() {
        User saved = users.save(user("anna@example.com"));

        assertThat(users.findByEmail("anna@example.com")).map(User::getId).contains(saved.getId());
        assertThat(users.findByEmail("ghost@example.com")).isEmpty();
    }

    @Test
    void reportsExistingEmail() {
        users.save(user("anna@example.com"));

        assertThat(users.existsByEmail("anna@example.com")).isTrue();
        assertThat(users.existsByEmail("ghost@example.com")).isFalse();
    }

    @Test
    void duplicateEmailIsRejectedByDatabase() {
        users.save(user("anna@example.com"));

        assertThatThrownBy(() -> users.save(user("anna@example.com"))).isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void unknownIdGivesEmpty() {
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
    }
}
