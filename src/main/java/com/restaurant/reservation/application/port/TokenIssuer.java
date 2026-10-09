package com.restaurant.reservation.application.port;

import com.restaurant.reservation.domain.model.User;

public interface TokenIssuer {

    IssuedToken issue(User user);
}
