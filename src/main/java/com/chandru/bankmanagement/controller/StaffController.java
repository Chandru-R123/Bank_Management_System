package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.ActionResponse;
import com.chandru.bankmanagement.dto.PasswordRequest;
import com.chandru.bankmanagement.dto.StaffRequest;
import com.chandru.bankmanagement.dto.StaffResponse;
import com.chandru.bankmanagement.dto.StaffRolesRequest;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Staff login management (ADMIN only). Staff exist only in Keycloak.
 *
 *   GET  /api/admin/staff                            list staff with their roles
 *   POST /api/admin/staff                            create (email link or temporary password)
 *   PUT  /api/admin/staff/{id}/roles                 replace staff roles
 *   POST /api/admin/staff/{id}/enable | /disable
 *   POST /api/admin/staff/{id}/password-email        email a reset link
 *   PUT  /api/admin/staff/{id}/password              set a temporary password
 */
@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize(Roles.ADMIN)
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return staffService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActionResponse<StaffResponse> create(@Valid @RequestBody StaffRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return staffService.create(request, SecurityUtils.actor(jwt));
    }

    @PutMapping("/{id}/roles")
    public StaffResponse updateRoles(@PathVariable String id,
                                     @Valid @RequestBody StaffRolesRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return staffService.updateRoles(id, request.roles(), SecurityUtils.actor(jwt));
    }

    @PostMapping("/{id}/enable")
    public StaffResponse enable(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return staffService.setEnabled(id, true, SecurityUtils.actor(jwt));
    }

    @PostMapping("/{id}/disable")
    public StaffResponse disable(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return staffService.setEnabled(id, false, SecurityUtils.actor(jwt));
    }

    @PostMapping("/{id}/password-email")
    public ActionResponse<StaffResponse> sendPasswordEmail(@PathVariable String id) {
        return staffService.sendPasswordEmail(id);
    }

    @PutMapping("/{id}/password")
    public ActionResponse<StaffResponse> setPassword(@PathVariable String id,
                                                     @Valid @RequestBody PasswordRequest request) {
        return staffService.setTemporaryPassword(id, request.password());
    }
}
