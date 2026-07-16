package com.unifiedcalendar.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionAuthFilter sessionAuthFilter;

    public SecurityConfig(SessionAuthFilter sessionAuthFilter) {
        this.sessionAuthFilter = sessionAuthFilter;
    }

    /**
     * Configures the security filter chain: public routes, session policy, CORS, and a
     * 401-returning entry point (Spring Security defaults to 403 when form login is disabled).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disabled — JSON API; session cookie uses SameSite=Strict
            .csrf(AbstractHttpConfigurer::disable)
            // Delegate CORS preflight to CorsConfig so OPTIONS requests are not blocked
            .cors(Customizer.withDefaults())
            // Promote the custom adminId session attribute to a Spring Security Authentication
            // so that protected routes are gated by two independent mechanisms.
            .addFilterBefore(sessionAuthFilter, AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/**",
                    "/s/**",
                    "/availability/**",
                    "/bookings",
                    "/bookings/*/cancel",
                    "/bookings/*/reschedule",
                    "/actuator/health",
                    // Callbacks are secured by the HMAC-signed state parameter rather than session.
                    "/calendar/google/callback",
                    "/calendar/outlook/callback"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // Disable Spring Security's default login page — we manage sessions manually
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // Without this, Spring Security returns 403 for unauthenticated requests when
            // both formLogin and httpBasic are disabled. REST clients expect 401.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
            );

        return http.build();
    }

    /** Prevents Spring Boot from registering SessionAuthFilter in the servlet filter chain
     *  a second time; it is already registered inside the Spring Security filter chain above. */
    @Bean
    public FilterRegistrationBean<SessionAuthFilter> sessionAuthFilterRegistration() {
        FilterRegistrationBean<SessionAuthFilter> registration = new FilterRegistrationBean<>(sessionAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    /** BCrypt encoder used for admin password hashing throughout the auth flow. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
