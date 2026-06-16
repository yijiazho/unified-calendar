package com.unifiedcalendar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfig")
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("public endpoints pass through security")
    class PublicEndpoints {

        @Test
        @DisplayName("/actuator/health returns 200")
        void actuatorHealthIsAccessible() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/availability/** is permitted — 404 from missing handler, not 401")
        void availabilityIsPermitted() throws Exception {
            mvc.perform(get("/availability/slots"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("/s/** is permitted — 404 from missing handler, not 401")
        void shortLinkIsPermitted() throws Exception {
            mvc.perform(get("/s/my-slug"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("/auth/** is permitted — 404 from missing handler, not 401")
        void authRouteIsPermitted() throws Exception {
            mvc.perform(get("/auth/status"))
                    .andExpect(result ->
                        assertNotEquals(401, result.getResponse().getStatus()));
        }
    }

    @Nested
    @DisplayName("protected endpoints require authentication")
    class ProtectedEndpoints {

        @Test
        @DisplayName("/calendar/events returns 401 without session")
        void calendarEventsRequiresAuth() throws Exception {
            mvc.perform(get("/calendar/events"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/working-hours returns 401 without session")
        void workingHoursRequiresAuth() throws Exception {
            mvc.perform(get("/working-hours"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/calendar/accounts returns 401 without session")
        void calendarAccountsRequiresAuth() throws Exception {
            mvc.perform(get("/calendar/accounts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /calendar/accounts/{id} returns 401 without session")
        void deleteCalendarAccountRequiresAuth() throws Exception {
            mvc.perform(delete("/calendar/accounts/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /calendar/primary returns 401 without session")
        void setPrimaryRequiresAuth() throws Exception {
            mvc.perform(put("/calendar/primary")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"accountId\":1}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
