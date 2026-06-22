package com.unifiedcalendar.workinghours;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("WorkingHoursController integration")
class WorkingHoursControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static final String SIGNUP_URL       = "/auth/signup";
    private static final String LOGIN_URL        = "/auth/login";
    private static final String WORKING_HOURS_URL = "/working-hours";

    @Test
    @DisplayName("GET /working-hours returns 200 with empty array when no hours configured")
    void getReturnsEmptyArrayWhenNoneConfigured() throws Exception {
        MockHttpSession session = signupAndLogin("a@example.com", "pass", "slug-a");

        mvc.perform(get(WORKING_HOURS_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("PUT /working-hours with valid Monday-Friday 09:00-17:00 saves 5 rows and returns them")
    void putValidWeekdaysSavesFiveRows() throws Exception {
        MockHttpSession session = signupAndLogin("b@example.com", "pass", "slug-b");

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weekdayJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].dayOfWeek").value(0))
                .andExpect(jsonPath("$[0].startTime").value("09:00"))
                .andExpect(jsonPath("$[0].endTime").value("17:00"));
    }

    @Test
    @DisplayName("PUT /working-hours with empty array deletes all working hours")
    void putEmptyArrayDeletesAll() throws Exception {
        MockHttpSession session = signupAndLogin("c@example.com", "pass", "slug-c");

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session).contentType(MediaType.APPLICATION_JSON).content(weekdayJson()))
                .andExpect(status().isOk());

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("PUT /working-hours with duplicate dayOfWeek returns 400")
    void putDuplicateDayReturns400() throws Exception {
        MockHttpSession session = signupAndLogin("d@example.com", "pass", "slug-d");

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"dayOfWeek":1,"startTime":"09:00","endTime":"17:00"},
                                  {"dayOfWeek":1,"startTime":"10:00","endTime":"18:00"}
                                ]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /working-hours where startTime >= endTime returns 400")
    void putStartNotBeforeEndReturns400() throws Exception {
        MockHttpSession session = signupAndLogin("e@example.com", "pass", "slug-e");

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"dayOfWeek":0,"startTime":"17:00","endTime":"09:00"}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("PUT /working-hours with dayOfWeek=7 returns 400")
    void putDayOfWeekSevenReturns400() throws Exception {
        MockHttpSession session = signupAndLogin("f@example.com", "pass", "slug-f");

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"dayOfWeek":7,"startTime":"09:00","endTime":"17:00"}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /working-hours without session returns 401")
    void getWithoutSessionReturns401() throws Exception {
        mvc.perform(get(WORKING_HOURS_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /working-hours without session returns 401")
    void putWithoutSessionReturns401() throws Exception {
        mvc.perform(put(WORKING_HOURS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /working-hours after PUT reflects the saved hours")
    void getAfterPutReflectsSavedState() throws Exception {
        MockHttpSession session = signupAndLogin("g@example.com", "pass", "slug-g");

        mvc.perform(put(WORKING_HOURS_URL)
                        .session(session).contentType(MediaType.APPLICATION_JSON).content(weekdayJson()))
                .andExpect(status().isOk());

        mvc.perform(get(WORKING_HOURS_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private MockHttpSession signupAndLogin(String email, String password, String slug) throws Exception {
        mvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\",\"timezone\":\"UTC\"}",
                                email, password, slug)))
                .andExpect(status().isCreated());

        MvcResult result = mvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    private String weekdayJson() {
        return """
                [
                  {"dayOfWeek":0,"startTime":"09:00","endTime":"17:00"},
                  {"dayOfWeek":1,"startTime":"09:00","endTime":"17:00"},
                  {"dayOfWeek":2,"startTime":"09:00","endTime":"17:00"},
                  {"dayOfWeek":3,"startTime":"09:00","endTime":"17:00"},
                  {"dayOfWeek":4,"startTime":"09:00","endTime":"17:00"}
                ]""";
    }
}
