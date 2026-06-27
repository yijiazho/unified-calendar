package com.unifiedcalendar.calendar.sync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Provides a do-nothing CalendarSyncer for the test profile so controllers can inject it normally. */
@Configuration
@Profile("test")
class NoOpCalendarSyncConfig {

    @Bean
    CalendarSyncer noOpCalendarSyncer() {
        return () -> {};
    }
}
