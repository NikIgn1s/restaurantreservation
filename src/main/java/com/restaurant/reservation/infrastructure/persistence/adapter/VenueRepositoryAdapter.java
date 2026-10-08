package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.VenueRepository;
import com.restaurant.reservation.infrastructure.persistence.mapper.VenueMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.VenueJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {

    private final VenueJpaRepository jpa;
    private final VenueMapper mapper;

    @Override
    @Transactional
    public Venue save(Venue venue) {
        return mapper.toDomain(jpa.save(mapper.toEntity(venue)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Venue> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venue> findByOwnerId(UUID ownerId) {
        return jpa.findByOwnerId(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venue> findAllActive() {
        return jpa.findByActiveTrue().stream().map(mapper::toDomain).toList();
    }
}
