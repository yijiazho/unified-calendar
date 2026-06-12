package com.unifiedcalendar.auth;

import jakarta.servlet.http.HttpSession;

/** Reads the authenticated admin ID from the session; throws UnauthorizedException if absent. */
public class SessionUtils {

    private SessionUtils() {}

    public static Long requireAdminId(HttpSession session) {
        Object adminId = session.getAttribute("adminId");
        if (!(adminId instanceof Long id)) {
            throw new UnauthorizedException();
        }
        return id;
    }
}
