package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.infrastructure.persistence.mapper.ResourceMapper;
import com.restaurant.reservation.infrastructure.persistence.repository.ResourceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ResourceRepositoryAdapter implements ResourceRepository {

    private final ResourceJpaRepository jpa;
    private final ResourceMapper mapper;

    @Override
    @Transactional
    public Resource save(Resource resource) {
        return mapper.toDomain(jpa.save(mapper.toEntity(resource)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Resource> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resource> findByVenueId(UUID venueId) {
        return jpa.findByVenueId(venueId).stream().map(mapper::toDomain).toList();
    }
}
