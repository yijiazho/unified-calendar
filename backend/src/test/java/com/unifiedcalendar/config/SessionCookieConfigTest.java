package com.unifiedcalendar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Session cookie configuration")
class SessionCookieConfigTest {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    @DisplayName("session cookie has HttpOnly flag — prevents JS access to JSESSIONID")
    void sessionCookieIsHttpOnly() {
        assertTrue(
                Boolean.TRUE.equals(serverProperties.getServlet().getSession().getCookie().getHttpOnly()),
                "server.servlet.session.cookie.http-only must be true");
    }

    @Test
    @DisplayName("session cookie has SameSite=Strict — CSRF mitigation in place of CSRF tokens")
    void sessionCookieIsSameSiteStrict() {
        assertEquals(Cookie.SameSite.STRICT,
                serverProperties.getServlet().getSession().getCookie().getSameSite(),
                "server.servlet.session.cookie.same-site must be strict");
    }
}
