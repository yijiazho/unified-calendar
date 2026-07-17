package com.unifiedcalendar.booking;

import com.unifiedcalendar.calendar.CalendarEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the local cancellation changes atomically after the provider call has completed. */
@Service
public class CancellationPersistenceService {

    private final BookingRepository bookingRepository;
    private final CalendarEventRepository calendarEventRepository;

    public CancellationPersistenceService(
            BookingRepository bookingRepository,
            CalendarEventRepository calendarEventRepository) {
        this.bookingRepository = bookingRepository;
        this.calendarEventRepository = calendarEventRepository;
    }

    /**
     * Marks a confirmed booking cancelled and removes its cached event in one transaction.
     * The conditional status update makes a concurrent second cancellation lose cleanly.
     */
    @Transactional
    public boolean persistCancellation(Booking booking) {
        boolean changed = bookingRepository.updateStatusIfCurrent(
                booking.id(), "CONFIRMED", "CANCELLED");
        if (!changed) {
            return false;
        }
        calendarEventRepository.deleteById(booking.calendarEventId(), booking.adminId());
        return true;
    }
}
