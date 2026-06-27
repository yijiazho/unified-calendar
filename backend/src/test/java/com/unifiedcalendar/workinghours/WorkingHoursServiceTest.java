package com.unifiedcalendar.workinghours;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AuthService;
import com.unifiedcalendar.auth.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("WorkingHoursService")
class WorkingHoursServiceTest {

    @Autowired
    private WorkingHoursService service;

    @Autowired
    private AuthService authService;

    private Long adminId;

    @BeforeEach
    void setUp() {
        Admin admin = authService.signup("wh-test@example.com", "password", "wh-test-slug", "UTC");
        adminId = admin.id();
    }

    @Test
    @DisplayName("getWorkingHours returns empty list when no hours configured")
    void getReturnsEmptyWhenNoneConfigured() {
        assertTrue(service.getWorkingHours(adminId).isEmpty());
    }

    @Test
    @DisplayName("saveWorkingHours persists valid Monday–Friday 09:00–17:00 and returns 5 rows")
    void saveValidWeekdaysReturnsFiveRows() {
        List<WorkingHoursDto> saved = service.saveWorkingHours(adminId, weekdayHours());

        assertEquals(5, saved.size());
        assertEquals(0, saved.get(0).dayOfWeek());
        assertEquals("09:00", saved.get(0).startTime());
        assertEquals("17:00", saved.get(0).endTime());
    }

    @Test
    @DisplayName("saveWorkingHours with empty array deletes all working hours")
    void saveEmptyArrayClearsAll() {
        service.saveWorkingHours(adminId, weekdayHours());
        service.saveWorkingHours(adminId, List.of());

        assertTrue(service.getWorkingHours(adminId).isEmpty());
    }

    @Test
    @DisplayName("saveWorkingHours with null list throws ValidationException")
    void saveNullListThrows() {
        assertThrows(ValidationException.class, () -> service.saveWorkingHours(adminId, null));
    }

    @Test
    @DisplayName("saveWorkingHours with null entry throws ValidationException")
    void saveNullEntryThrows() {
        List<WorkingHoursDto> dtos = new ArrayList<>();
        dtos.add(null);
        assertThrows(ValidationException.class, () -> service.saveWorkingHours(adminId, dtos));
    }

    @Test
    @DisplayName("saveWorkingHours with duplicate dayOfWeek throws ValidationException")
    void saveDuplicateDayThrows() {
        List<WorkingHoursDto> dtos = List.of(
                new WorkingHoursDto(1, "09:00", "17:00"),
                new WorkingHoursDto(1, "10:00", "18:00")
        );
        assertThrows(ValidationException.class, () -> service.saveWorkingHours(adminId, dtos));
    }

    @Test
    @DisplayName("saveWorkingHours where startTime > endTime throws ValidationException")
    void saveStartAfterEndThrows() {
        List<WorkingHoursDto> dtos = List.of(new WorkingHoursDto(0, "17:00", "09:00"));
        assertThrows(ValidationException.class, () -> service.saveWorkingHours(adminId, dtos));
    }

    @Test
    @DisplayName("saveWorkingHours where startTime equals endTime throws ValidationException")
    void saveStartEqualsEndThrows() {
        List<WorkingHoursDto> dtos = List.of(new WorkingHoursDto(0, "09:00", "09:00"));
        assertThrows(ValidationException.class, () -> service.saveWorkingHours(adminId, dtos));
    }

    @Test
    @DisplayName("saveWorkingHours accepts all 7 days without throwing")
    void saveAllSevenDaysAccepted() {
        List<WorkingHoursDto> dtos = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            dtos.add(new WorkingHoursDto(i, "08:00", "20:00"));
        }
        assertDoesNotThrow(() -> service.saveWorkingHours(adminId, dtos));
        assertEquals(7, service.getWorkingHours(adminId).size());
    }

    private List<WorkingHoursDto> weekdayHours() {
        List<WorkingHoursDto> dtos = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            dtos.add(new WorkingHoursDto(i, "09:00", "17:00"));
        }
        return dtos;
    }
}
