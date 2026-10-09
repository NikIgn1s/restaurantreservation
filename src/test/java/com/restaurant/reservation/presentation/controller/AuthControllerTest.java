package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AuthResult;
import com.restaurant.reservation.application.dto.LoginCommand;
import com.restaurant.reservation.application.dto.RegisterUserCommand;
import com.restaurant.reservation.application.port.IssuedToken;
import com.restaurant.reservation.application.usecase.GetCurrentUserUseCase;
import com.restaurant.reservation.application.usecase.LoginUseCase;
import com.restaurant.reservation.application.usecase.RegisterUserUseCase;
import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidCredentialsException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.presentation.mapper.AuthApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static com.restaurant.reservation.presentation.TestAuth.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, AuthApiMapper.class})
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final String REGISTER = """
            {"email": "anna@example.com", "password": "password123", "fullName": "Anna Kowalska", "role": "CUSTOMER"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private RegisterUserUseCase registerUser;
    @MockBean
    private LoginUseCase login;
    @MockBean
    private GetCurrentUserUseCase currentUser;

    private User user() {
        return User.register("anna@example.com", "hash", "Anna Kowalska", UserRole.CUSTOMER, NOW);
    }

    @Test
    void registersUserWithoutExposingPasswordHash() throws Exception {
        User user = user();
        when(registerUser.execute(any())).thenReturn(user);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.fullName").value("Anna Kowalska"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void passesRegistrationDataToUseCase() throws Exception {
        when(registerUser.execute(any())).thenReturn(user());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER));

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUser).execute(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("anna@example.com");
        assertThat(captor.getValue().password()).isEqualTo("password123");
        assertThat(captor.getValue().role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void registrationDoesNotRequireAuthenticationHeaders() throws Exception {
        when(registerUser.execute(any())).thenReturn(user());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        when(registerUser.execute(any())).thenThrow(new EmailAlreadyUsedException("anna@example.com"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void adminRegistrationIsForbidden() throws Exception {
        when(registerUser.execute(any())).thenThrow(new ForbiddenOperationException("Administrator accounts cannot be self-registered"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.replace("CUSTOMER", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsEveryInvalidRegistrationField() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"short\", \"fullName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.role").exists());

        verify(registerUser, never()).execute(any());
    }

    @Test
    void logsInAndReturnsBearerToken() throws Exception {
        User user = user();
        when(login.execute(any())).thenReturn(new AuthResult(user, new IssuedToken("jwt-value", NOW.plusSeconds(3600))));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"anna@example.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.user.email").value("anna@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(login).execute(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("anna@example.com");
        assertThat(captor.getValue().password()).isEqualTo("password123");
    }

    @Test
    void wrongCredentialsAreUnauthorized() throws Exception {
        when(login.execute(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"anna@example.com\", \"password\": \"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    void returnsProfileOfAuthenticatedUser() throws Exception {
        User user = user();
        when(currentUser.execute(any())).thenReturn(user);

        mockMvc.perform(get("/api/auth/me").with(asUser(user.getId(), UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<Actor> captor = ArgumentCaptor.forClass(Actor.class);
        verify(currentUser).execute(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(user.getId());
        assertThat(captor.getValue().role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
