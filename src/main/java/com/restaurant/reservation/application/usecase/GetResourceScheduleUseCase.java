package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetResourceScheduleUseCase {

    private final ResourceRepository resources;
    private final ScheduleRepository schedules;

    @Transactional(readOnly = true)
    public List<Schedule> execute(UUID resourceId) {
        if (resources.findById(resourceId).isEmpty()) {
            throw new NotFoundException("Resource " + resourceId + " not found");
        }
        return schedules.findByResourceId(resourceId).stream()
                .sorted(Comparator.comparing(Schedule::getDayOfWeek))
                .toList();
    }
}
