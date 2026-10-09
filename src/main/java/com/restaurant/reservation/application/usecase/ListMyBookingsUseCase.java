package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListMyBookingsUseCase {

    private final BookingRepository bookings;

    @Transactional(readOnly = true)
    public List<Booking> execute(Actor actor) {
        return bookings.findByUserId(actor.id());
    }
}
