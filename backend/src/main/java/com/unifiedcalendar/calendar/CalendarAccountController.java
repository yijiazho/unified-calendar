package com.unifiedcalendar.calendar;

import com.unifiedcalendar.auth.SessionUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/calendar")
public class CalendarAccountController {

    private final CalendarAccountRepository repository;

    public CalendarAccountController(CalendarAccountRepository repository) {
        this.repository = repository;
    }

    /** Returns all Google and Outlook calendar accounts connected by the authenticated admin. */
    @GetMapping("/accounts")
    public List<CalendarAccountResponse> listAccounts(HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        return repository.findAllByAdminId(adminId).stream()
                .map(CalendarAccountResponse::from)
                .toList();
    }

    /** Removes a connected calendar account and its cached events (cascades via FK). */
    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable Long id, HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        repository.delete(id, adminId);
    }

    /** Sets exactly one account as the admin's primary calendar; the previous primary is cleared atomically. */
    @PutMapping("/primary")
    public List<CalendarAccountResponse> setPrimary(
            @RequestBody SetPrimaryRequest request,
            HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        // Note: findById and setPrimary are separate transactions.
        // Acceptable for MVP single-session use; consolidate into a single transactional
        // service method if concurrent admin sessions are introduced in Phase 2.
        repository.findById(request.accountId(), adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Calendar account not found"));
        repository.setPrimary(request.accountId(), adminId);
        return repository.findAllByAdminId(adminId).stream()
                .map(CalendarAccountResponse::from)
                .toList();
    }
}
