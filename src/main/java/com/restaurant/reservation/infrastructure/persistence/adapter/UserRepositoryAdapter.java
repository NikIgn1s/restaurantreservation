package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.repository.UserRepository;
import com.restaurant.reservation.infrastructure.persistence.mapper.UserMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;
    private final UserMapper mapper;

    @Override
    @Transactional
    public User save(User user) {
        try {
            return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(user.getEmail());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }
}
