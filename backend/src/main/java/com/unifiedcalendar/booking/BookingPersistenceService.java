package com.unifiedcalendar.booking;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles transactional persistence of bookings.
 * By being a separate proxied bean, its @Transactional methods are properly intercepted.
 * This ensures calendar_events and bookings inserts are atomic: both succeed or both roll back.
 */
@Service
public class BookingPersistenceService {

    private final CalendarEventRepository calendarEventRepository;
    private final BookingRepository bookingRepository;

    public BookingPersistenceService(
            CalendarEventRepository calendarEventRepository,
            BookingRepository bookingRepository) {
        this.calendarEventRepository = calendarEventRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Atomically inserts calendar_event and booking records.
     * Fails entirely if either insert fails (no partial writes).
     */
    @Transactional
    public Booking persistBooking(Admin admin, CalendarAccount primary, String providerEventId,
                                  String title, Instant slotStart, Instant slotEnd,
                                  String visitorName, String visitorEmail, String visitorPhone, String notes) {
        // Insert calendar event first
        CalendarEvent calEvent = new CalendarEvent(
                null, admin.id(), primary.id(), primary.provider(),
                providerEventId, title, slotStart, slotEnd, true, Instant.now(), Instant.now());
        Long calEventId = calendarEventRepository.insertBookingEvent(calEvent);

        // Then insert booking (if this fails, calendar_event is rolled back)
        Booking booking = new Booking(
                null, admin.id(), calEventId,
                visitorName, visitorEmail, visitorPhone, notes,
                "CONFIRMED",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                Instant.now());
        return bookingRepository.save(booking);
    }
}
