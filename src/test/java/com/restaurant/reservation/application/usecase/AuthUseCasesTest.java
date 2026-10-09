package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AuthResult;
import com.restaurant.reservation.application.dto.LoginCommand;
import com.restaurant.reservation.application.dto.RegisterUserCommand;
import com.restaurant.reservation.application.port.IssuedToken;
import com.restaurant.reservation.application.port.PasswordHasher;
import com.restaurant.reservation.application.port.TokenIssuer;
import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidCredentialsException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCasesTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private UserRepository users;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenIssuer tokenIssuer;

    private RegisterUserUseCase register;
    private LoginUseCase login;

    @BeforeEach
    void setUp() {
        register = new RegisterUserUseCase(users, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC));
        login = new LoginUseCase(users, passwordHasher, tokenIssuer);
    }

    private RegisterUserCommand registration(String email, String password, UserRole role) {
        return new RegisterUserCommand(email, password, "Anna Kowalska", role);
    }

    @Test
    void registersUserWithHashedPasswordAndNormalizedEmail() {
        when(users.existsByEmail("anna@example.com")).thenReturn(false);
        when(passwordHasher.hash("password123")).thenReturn("hashed");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User user = register.execute(registration("  Anna@Example.com ", "password123", UserRole.CUSTOMER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(user);
        assertThat(user.getEmail()).isEqualTo("anna@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void venueOwnerMayRegister() {
        when(users.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordHasher.hash("password123")).thenReturn("hashed");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(register.execute(registration("owner@example.com", "password123", UserRole.VENUE_OWNER)).getRole())
                .isEqualTo(UserRole.VENUE_OWNER);
    }

    @Test
    void adminCannotBeSelfRegistered() {
        assertThatThrownBy(() -> register.execute(registration("admin@example.com", "password123", UserRole.ADMIN)))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(users, passwordHasher);
    }

    @Test
    void rejectsDuplicateEmail() {
        when(users.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> register.execute(registration("ANNA@example.com", "password123", UserRole.CUSTOMER)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsTooShortPassword() {
        assertThatThrownBy(() -> register.execute(registration("a@b.c", "short", UserRole.CUSTOMER)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, passwordHasher);
    }

    @Test
    void rejectsTooLongPassword() {
        assertThatThrownBy(() -> register.execute(registration("a@b.c", "x".repeat(73), UserRole.CUSTOMER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPassword() {
        assertThatThrownBy(() -> register.execute(registration("a@b.c", null, UserRole.CUSTOMER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logsInAndIssuesToken() {
        User user = User.register("anna@example.com", "hashed", "Anna", UserRole.CUSTOMER, NOW);
        IssuedToken token = new IssuedToken("jwt", NOW.plusSeconds(3600));
        when(users.findByEmail("anna@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("password123", "hashed")).thenReturn(true);
        when(tokenIssuer.issue(user)).thenReturn(token);

        AuthResult result = login.execute(new LoginCommand("  ANNA@example.com ", "password123"));

        assertThat(result.user()).isSameAs(user);
        assertThat(result.token()).isEqualTo(token);
    }

    @Test
    void wrongPasswordIsRejected() {
        User user = User.register("anna@example.com", "hashed", "Anna", UserRole.CUSTOMER, NOW);
        when(users.findByEmail("anna@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> login.execute(new LoginCommand("anna@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void unknownEmailGivesTheSameError() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> login.execute(new LoginCommand("ghost@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verifyNoInteractions(tokenIssuer);
    }
}
