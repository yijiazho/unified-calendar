package com.unifiedcalendar.workinghours;

import com.unifiedcalendar.auth.ValidationException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class WorkingHoursService {

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

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
            if (dto.dayOfWeek() == null) {
                throw new ValidationException("dayOfWeek is required");
            }
            if (dto.dayOfWeek() < 0 || dto.dayOfWeek() > 6) {
                throw new ValidationException("dayOfWeek must be between 0 and 6");
            }
            if (!seen.add(dto.dayOfWeek())) {
                throw new ValidationException("Duplicate dayOfWeek: " + dto.dayOfWeek());
            }
            if (!isValidTime(dto.startTime())) {
                throw new ValidationException("Invalid startTime: " + dto.startTime());
            }
            if (!isValidTime(dto.endTime())) {
                throw new ValidationException("Invalid endTime: " + dto.endTime());
            }
            if (dto.startTime().compareTo(dto.endTime()) >= 0) {
                throw new ValidationException("startTime must be strictly before endTime for dayOfWeek " + dto.dayOfWeek());
            }
        }
    }

    private boolean isValidTime(String time) {
        return time != null && TIME_PATTERN.matcher(time).matches();
    }
}
