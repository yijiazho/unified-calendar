package com.unifiedcalendar.booking;

import com.unifiedcalendar.calendar.CalendarEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Applies local rescheduling changes atomically after the provider update succeeds. */
@Service
public class ReschedulePersistenceService {

    private final CalendarEventRepository calendarEventRepository;
    private final SlotReservationRepository slotReservationRepository;

    public ReschedulePersistenceService(
            CalendarEventRepository calendarEventRepository,
            SlotReservationRepository slotReservationRepository) {
        this.calendarEventRepository = calendarEventRepository;
        this.slotReservationRepository = slotReservationRepository;
    }

    @Transactional
    public void persistReschedule(Booking booking, Instant newStart, Instant newEnd,
                                  Long reservationId) {
        calendarEventRepository.updateTime(
                booking.calendarEventId(), booking.adminId(), newStart, newEnd);
        // The event becoming busy and the destination reservation disappearing must be atomic.
        slotReservationRepository.delete(reservationId);
    }
}
