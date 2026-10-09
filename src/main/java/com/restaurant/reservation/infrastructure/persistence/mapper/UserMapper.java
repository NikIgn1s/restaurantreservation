package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getFullName(),
                entity.getRole(), entity.getCreatedAt());
    }

    public UserEntity toEntity(User user) {
        return new UserEntity(user.getId(), user.getEmail(), user.getPasswordHash(), user.getFullName(),
                user.getRole(), user.getCreatedAt());
    }
}
