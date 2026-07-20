package com.unifiedcalendar.availability;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PublicController {

    private final AdminRepository adminRepository;

    public PublicController(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    /** Returns the public profile for an admin slug, used by the visitor scheduling page. */
    @GetMapping("/s/{slug}")
    public AdminPublicInfoResponse getAdminInfo(@PathVariable String slug) {
        Admin admin = adminRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        return new AdminPublicInfoResponse(admin.slug(), admin.displayName(), admin.timezone());
    }
}
