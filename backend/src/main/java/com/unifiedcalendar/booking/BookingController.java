package com.unifiedcalendar.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CancellationService cancellationService;
    private final RescheduleService rescheduleService;

    public BookingController(BookingService bookingService, CancellationService cancellationService,
                             RescheduleService rescheduleService) {
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
        this.rescheduleService = rescheduleService;
    }

    /** Accepts a visitor booking request and returns the confirmed booking with cancel/reschedule tokens. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(request);
    }

    /** Cancels a booking using the visitor-facing token; no authenticated session is required. */
    @PostMapping("/{cancelToken}/cancel")
    public CancellationResponse cancelBooking(@PathVariable String cancelToken) {
        return cancellationService.cancel(cancelToken);
    }

    /** Moves a booking using its visitor-facing reschedule token; no session is required. */
    @PostMapping("/{rescheduleToken}/reschedule")
    public RescheduleResponse rescheduleBooking(
            @PathVariable String rescheduleToken,
            @Valid @RequestBody RescheduleRequest request) {
        return rescheduleService.reschedule(
                rescheduleToken, request.newSlotStart(), request.newSlotEnd());
    }
}
