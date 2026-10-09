package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.RegisterUserCommand;
import com.restaurant.reservation.application.port.PasswordHasher;
import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Transactional
    public User execute(RegisterUserCommand command) {
        if (command.role() == UserRole.ADMIN) {
            throw new ForbiddenOperationException("Administrator accounts cannot be self-registered");
        }
        validatePassword(command.password());
        String email = User.normalizeEmail(command.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = User.register(email, passwordHasher.hash(command.password()), command.fullName(),
                command.role(), clock.instant());
        return users.save(user);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be between " + MIN_PASSWORD_LENGTH
                    + " and " + MAX_PASSWORD_LENGTH + " characters");
        }
    }
}
