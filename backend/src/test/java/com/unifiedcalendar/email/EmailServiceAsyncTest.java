package com.unifiedcalendar.email;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.booking.Booking;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import com.unifiedcalendar.calendar.Provider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(EmailServiceAsyncTest.Config.class)
class EmailServiceAsyncTest {

    @Autowired private EmailService emailService;
    @Autowired private ResendClient resendClient;
    @Autowired private IcsService icsService;
    @Autowired private CalendarEventRepository calendarEventRepository;

    @Test
    void publicSendMethodReturnsWithoutWaitingForResend() throws Exception {
        Booking booking = new Booking(
                7L, 1L, 22L, "Jane", "jane@example.com", null, null,
                "CONFIRMED", "cancel-token", "reschedule-token", Instant.now());
        Admin admin = new Admin(1L, "owner@example.com", "hash", "owner", "UTC", Instant.now(), Instant.now());
        CalendarEvent event = new CalendarEvent(
                22L, 1L, 3L, Provider.GOOGLE, "provider-id", "Appointment",
                Instant.parse("2024-03-15T14:00:00Z"), Instant.parse("2024-03-15T14:30:00Z"),
                true, Instant.now(), Instant.now());
        when(calendarEventRepository.findById(22L)).thenReturn(Optional.of(event));
        when(icsService.generate(any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});

        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            releaseSend.await(5, TimeUnit.SECONDS);
            return null;
        }).when(resendClient).send(any());

        long startNanos = System.nanoTime();
        emailService.sendBookingEmails(booking, admin);
        Duration callDuration = Duration.ofNanos(System.nanoTime() - startNanos);

        try {
            assertThat(callDuration).isLessThan(Duration.ofMillis(500));
            assertThat(sendStarted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseSend.countDown();
        }
    }

    @Configuration
    @EnableAsync
    static class Config {
        @Bean(name = "emailTaskExecutor")
        ThreadPoolTaskExecutor emailTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            return executor;
        }

        @Bean ResendClient resendClient() {
            return mock(ResendClient.class);
        }

        @Bean IcsService icsService() {
            return mock(IcsService.class);
        }

        @Bean CalendarEventRepository calendarEventRepository() {
            return mock(CalendarEventRepository.class);
        }

        @Bean EmailService emailService(ResendClient resendClient, IcsService icsService,
                                        CalendarEventRepository calendarEventRepository) {
            return new EmailService(
                    resendClient, icsService, calendarEventRepository,
                    "sender@example.com", "https://calendar.example");
        }
    }
}
