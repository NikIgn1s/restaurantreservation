package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public User execute(Actor actor) {
        return users.findById(actor.id())
                .orElseThrow(() -> new NotFoundException("User " + actor.id() + " not found"));
    }
}
