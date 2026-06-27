package com.unifiedcalendar.workinghours;

import com.unifiedcalendar.auth.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkingHoursService {

    private final WorkingHoursRepository repository;

    public WorkingHoursService(WorkingHoursRepository repository) {
        this.repository = repository;
    }

    /** Returns the configured availability windows for an admin, ordered by dayOfWeek. */
    public List<WorkingHoursDto> getWorkingHours(Long adminId) {
        return repository.findAllByAdminId(adminId).stream()
                .map(wh -> new WorkingHoursDto(wh.dayOfWeek(), wh.startTime(), wh.endTime()))
                .toList();
    }

    /** Validates then atomically replaces all working-hours rows; returns the saved state. */
    public List<WorkingHoursDto> saveWorkingHours(Long adminId, List<WorkingHoursDto> dtos) {
        validate(dtos);
        List<WorkingHours> entities = dtos.stream()
                .map(dto -> new WorkingHours(null, adminId, dto.dayOfWeek(), dto.startTime(), dto.endTime()))
                .toList();
        repository.replaceAll(adminId, entities);
        return getWorkingHours(adminId);
    }

    private void validate(List<WorkingHoursDto> dtos) {
        if (dtos == null) {
            throw new ValidationException("Request body must not be null");
        }
        if (dtos.size() > 7) {
            throw new ValidationException("At most 7 entries allowed (one per day)");
        }
        Set<Integer> seen = new HashSet<>();
        for (WorkingHoursDto dto : dtos) {
            if (dto == null) {
                throw new ValidationException("Working-hours entry must not be null");
            }
            // null/range/format checks are expressed by @NotNull, @Min, @Max, @Pattern on WorkingHoursDto.
            if (dto.dayOfWeek() != null && !seen.add(dto.dayOfWeek())) {
                throw new ValidationException("Duplicate dayOfWeek: " + dto.dayOfWeek());
            }
            // Null guard before LocalTime.parse defends against direct-service calls that bypass Bean Validation.
            if (dto.startTime() != null && dto.endTime() != null
                    && !LocalTime.parse(dto.startTime()).isBefore(LocalTime.parse(dto.endTime()))) {
                throw new ValidationException("startTime must be strictly before endTime for dayOfWeek " + dto.dayOfWeek());
            }
        }
    }
}
