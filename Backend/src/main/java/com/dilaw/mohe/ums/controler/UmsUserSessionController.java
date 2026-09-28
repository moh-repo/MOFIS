package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("api/v1/ums/sessions")
@PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
public class UmsUserSessionController {

    private static final Set<String> SESSION_SORT_FIELDS = Set.of(
            "id", "createdAt", "expiresAt", "ipAddress", "isActive", "user.userName", "user.email");

    @Autowired
    private UmsUserSessionService sessionService;

    @GetMapping("/")
    public ResponseEntity<Page<UmsUserSession>> getAllSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, SESSION_SORT_FIELDS, "createdAt");
        return ResponseEntity.ok(sessionService.listSessions(blankToNull(keyword), active, pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/active")
    public ResponseEntity<List<UmsUserSession>> getActiveSessions() {
        return ResponseEntity.ok(sessionService.getActiveSessions());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UmsUserSession>> getUserSessions(@PathVariable Long userId) {
        return ResponseEntity.ok(sessionService.getSessionsByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsUserSession> getSessionById(@PathVariable Long id) {
        return sessionService.getSessionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Void> terminateSession(@PathVariable Long id) {
        sessionService.terminateSession(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Void> terminateAllUserSessions(@PathVariable Long userId) {
        sessionService.terminateAllUserSessions(userId);
        return ResponseEntity.noContent().build();
    }
}
