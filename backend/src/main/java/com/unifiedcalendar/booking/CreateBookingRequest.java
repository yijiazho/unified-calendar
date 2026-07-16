package com.unifiedcalendar.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        @NotBlank(message = "must not be blank")
        String slug,

        @NotNull(message = "must not be null")
        String slotStart,

        @NotNull(message = "must not be null")
        String slotEnd,

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must not exceed 200 characters")
        String visitorName,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        String visitorEmail,

        @Size(max = 50, message = "must not exceed 50 characters")
        String visitorPhone,

        @Size(max = 2000, message = "must not exceed 2000 characters")
        String notes
) {}
