package com.unifiedcalendar.email;

import com.unifiedcalendar.booking.Booking;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    /** Sends confirmation emails to visitor and admin; runs asynchronously so the booking response is not delayed. */
    @Async
    public void sendBookingEmails(Booking booking) {
        // Implemented in TASK-020
    }
}
