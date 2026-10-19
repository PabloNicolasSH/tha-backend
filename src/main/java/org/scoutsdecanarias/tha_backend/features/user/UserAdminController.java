package org.scoutsdecanarias.tha_backend.features.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/account-requests")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARY')")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    List<UserAdminService.RequestSummary> list() {
        return service.openRequests();
    }

    @PostMapping("/{id}/approve")
    UserAdminService.RequestSummary approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/resend-invitation")
    UserAdminService.RequestSummary resend(@PathVariable Long id) {
        return service.resendInvitation(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> reject(@PathVariable Long id) {
        service.reject(id);
        return ResponseEntity.noContent().build();
    }
}
