package com.restaurant.reservation.presentation.mapper;

import com.restaurant.reservation.application.dto.AuthResult;
import com.restaurant.reservation.application.dto.LoginCommand;
import com.restaurant.reservation.application.dto.RegisterUserCommand;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.presentation.request.LoginRequest;
import com.restaurant.reservation.presentation.request.RegisterRequest;
import com.restaurant.reservation.presentation.response.TokenResponse;
import com.restaurant.reservation.presentation.response.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthApiMapper {

    private static final String BEARER = "Bearer";

    public RegisterUserCommand toCommand(RegisterRequest request) {
        return new RegisterUserCommand(request.email(), request.password(), request.fullName(), request.role());
    }

    public LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(request.email(), request.password());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }

    public TokenResponse toResponse(AuthResult result) {
        return new TokenResponse(result.token().value(), BEARER, result.token().expiresAt(), toResponse(result.user()));
    }
}
