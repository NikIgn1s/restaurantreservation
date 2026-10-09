package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AuthResult;
import com.restaurant.reservation.application.dto.LoginCommand;
import com.restaurant.reservation.application.port.PasswordHasher;
import com.restaurant.reservation.application.port.TokenIssuer;
import com.restaurant.reservation.domain.exception.InvalidCredentialsException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    @Transactional(readOnly = true)
    public AuthResult execute(LoginCommand command) {
        User user = users.findByEmail(User.normalizeEmail(command.email()))
                .filter(candidate -> passwordHasher.matches(command.password(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthResult(user, tokenIssuer.issue(user));
    }
}
