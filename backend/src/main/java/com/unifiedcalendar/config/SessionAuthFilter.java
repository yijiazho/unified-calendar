package com.unifiedcalendar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the custom adminId session attribute set by the login flow and promotes it to an
 * AdminAuthentication so that Spring Security's filter chain can gate on session presence.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("adminId") instanceof Long adminId) {
                SecurityContextHolder.getContext().setAuthentication(new AdminAuthentication(adminId));
            }
        }
        chain.doFilter(request, response);
    }
}
