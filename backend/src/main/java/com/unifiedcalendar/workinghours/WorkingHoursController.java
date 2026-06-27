package com.unifiedcalendar.workinghours;

import com.unifiedcalendar.auth.SessionUtils;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/working-hours")
public class WorkingHoursController {

    private final WorkingHoursService service;

    public WorkingHoursController(WorkingHoursService service) {
        this.service = service;
    }

    /** Returns the admin's configured availability windows; missing days are absent (unavailable). */
    @GetMapping
    public List<WorkingHoursDto> get(HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        return service.getWorkingHours(adminId);
    }

    /** Fully replaces the admin's working hours; days omitted from the body become unavailable. */
    @PutMapping
    public List<WorkingHoursDto> put(@Valid @RequestBody List<WorkingHoursDto> body, HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        return service.saveWorkingHours(adminId, body);
    }
}
