package com.unifiedcalendar.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Session-derived authentication token carrying the authenticated admin's id. */
public class AdminAuthentication extends AbstractAuthenticationToken {

    private final Long adminId;

    public AdminAuthentication(Long adminId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        this.adminId = adminId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    /** Returns the authenticated admin's database id. */
    @Override
    public Long getPrincipal() {
        return adminId;
    }
}
