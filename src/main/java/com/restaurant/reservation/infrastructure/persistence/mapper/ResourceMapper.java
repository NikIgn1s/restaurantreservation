package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.infrastructure.persistence.entity.ResourceEntity;
import org.springframework.stereotype.Component;

@Component
public class ResourceMapper {

    public Resource toDomain(ResourceEntity entity) {
        return new Resource(entity.getId(), entity.getVenueId(), entity.getName(), entity.getCapacity(), entity.isActive());
    }

    public ResourceEntity toEntity(Resource resource) {
        return new ResourceEntity(resource.getId(), resource.getVenueId(), resource.getName(),
                resource.getCapacity(), resource.isActive());
    }
}
