package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.usecase.GetCurrentUserUseCase;
import com.restaurant.reservation.application.usecase.LoginUseCase;
import com.restaurant.reservation.application.usecase.RegisterUserUseCase;
import com.restaurant.reservation.presentation.mapper.AuthApiMapper;
import com.restaurant.reservation.presentation.request.LoginRequest;
import com.restaurant.reservation.presentation.request.RegisterRequest;
import com.restaurant.reservation.presentation.resolver.CurrentActor;
import com.restaurant.reservation.presentation.response.TokenResponse;
import com.restaurant.reservation.presentation.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login and current user")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUseCase login;
    private final GetCurrentUserUseCase currentUser;
    private final AuthApiMapper mapper;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register a customer or a venue owner",
            description = "The email is case-insensitive and must be unique")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return mapper.toResponse(registerUser.execute(mapper.toCommand(request)));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and receive a JWT access token",
            description = "Dev users in the docker profile: owner@dev.local, alice@dev.local, bob@dev.local, admin@dev.local, password password123")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return mapper.toResponse(login.execute(mapper.toCommand(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the user who owns the token")
    public UserResponse me(@CurrentActor Actor actor) {
        return mapper.toResponse(currentUser.execute(actor));
    }
}
