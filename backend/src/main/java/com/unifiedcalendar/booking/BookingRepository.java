package com.unifiedcalendar.booking;

import java.util.Optional;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findByCancelToken(String token);
    Optional<Booking> findByRescheduleToken(String token);
    void updateStatus(Long id, String status);
}
