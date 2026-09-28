package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.ums.service.AccessReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The quarterly access review (S-04): list, attest, close, and open on demand. */
@RestController
@RequestMapping("api/v1/ums/access-reviews")
public class UmsAccessReviewController {

    public record DecisionRequest(String decision, String note) {}
    public record OpenRequest(String period) {}

    @Autowired private AccessReviewService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCESS_REVIEW.VIEW')")
    public ResponseEntity<List<AccessReviewService.ReviewView>> list() {
        return ResponseEntity.ok(service.visible());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCESS_REVIEW.VIEW')")
    public ResponseEntity<Map<String, Object>> one(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("review", service.view(service.require(id)), "items", service.items(id)));
    }

    @PostMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('ACCESS_REVIEW.EDIT')")
    public ResponseEntity<AccessReviewService.ItemView> decide(@PathVariable Long id, @PathVariable Long itemId,
                                                              @RequestBody DecisionRequest body) {
        return ResponseEntity.ok(service.decide(id, itemId, body.decision(), body.note(), SecurityRequestUtils.requireUserId()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ACCESS_REVIEW.EDIT')")
    public ResponseEntity<AccessReviewService.ReviewView> close(@PathVariable Long id) {
        return ResponseEntity.ok(service.close(id, SecurityRequestUtils.requireUserId()));
    }

    /** Opens the period's reviews now rather than waiting for the quarter; ministry administrators only. */
    @PostMapping("/open")
    @PreAuthorize("hasAuthority('ACCESS_REVIEW.EDIT') and (hasRole('MINISTRY_ADMIN') or hasRole('SUPER_ADMIN'))")
    public ResponseEntity<Map<String, Object>> open(@RequestBody(required = false) OpenRequest body) {
        String period = body != null && body.period() != null && !body.period().isBlank()
                ? body.period() : AccessReviewService.periodOf(LocalDate.now());
        int n = service.open(period, SecurityRequestUtils.requireUserId()).size();
        return ResponseEntity.ok(Map.of("period", period, "opened", n));
    }
}
