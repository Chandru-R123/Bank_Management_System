package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AuditLogResponse;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.service.AuditService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Audit log read API — staff only.
 *
 * GET /api/audit                                  ADMIN/CHECKER/EMPLOYEE — recent entries (paged)
 * GET /api/audit?page=0&size=50                   Paginated
 * GET /api/audit/actor/{userId}                   Events by actor
 * GET /api/audit/resource/{type}/{id}             Events for a specific resource
 */
@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @org.springframework.security.access.prepost.PreAuthorize(Roles.STAFF)
    @GetMapping
    public List<AuditLogResponse> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditService.getAll(page, Math.min(size, 200));
    }

    @org.springframework.security.access.prepost.PreAuthorize(Roles.STAFF)
    @GetMapping("/actor/{userId}")
    public List<AuditLogResponse> getByActor(@PathVariable String userId) {
        return auditService.getByActor(userId);
    }

    @org.springframework.security.access.prepost.PreAuthorize(Roles.STAFF)
    @GetMapping("/resource/{resourceType}/{resourceId}")
    public List<AuditLogResponse> getByResource(@PathVariable String resourceType,
                                                 @PathVariable String resourceId) {
        return auditService.getByResource(resourceType, resourceId);
    }
}
