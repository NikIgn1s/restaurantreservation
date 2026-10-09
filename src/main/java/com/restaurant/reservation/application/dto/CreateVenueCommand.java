package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.VenueType;

import java.time.ZoneId;

public record CreateVenueCommand(Actor actor, String name, VenueType type, String address, ZoneId timezone) {
}
