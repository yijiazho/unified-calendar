package com.unifiedcalendar.availability;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
public class AvailabilityController {

    private final AdminRepository adminRepository;
    private final AvailabilityService availabilityService;

    public AvailabilityController(AdminRepository adminRepository, AvailabilityService availabilityService) {
        this.adminRepository = adminRepository;
        this.availabilityService = availabilityService;
    }

    /** Returns available 30-minute slots for the admin identified by slug on the given date. */
    @GetMapping("/availability")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @RequestParam String slug,
            @RequestParam String date) {

        Admin admin = adminRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected YYYY-MM-DD");
        }

        List<TimeSlot> slots = availabilityService.getAvailableSlots(admin.id(), localDate);

        List<TimeSlotResponse> slotResponses = slots.stream()
                .map(s -> new TimeSlotResponse(s.start().toString(), s.end().toString()))
                .toList();

        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=60")
                .body(new AvailabilityResponse(date, admin.timezone(), slotResponses));
    }
}
