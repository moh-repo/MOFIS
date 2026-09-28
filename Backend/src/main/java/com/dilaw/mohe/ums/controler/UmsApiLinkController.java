package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.model.UmsApiLink;
import com.dilaw.mohe.ums.repository.UmsApiLinkRepository;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("api/v1/ums/api-links")
@PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_VIEW)
public class UmsApiLinkController {

    private static final Set<String> API_LINK_SORT_FIELDS = Set.of(
            "id", "resourceCode", "resourceName", "method", "urlPattern", "displayOrder", "isActive");

    @Autowired
    private UmsApiLinkRepository apiLinkRepository;

    @GetMapping("/")
    public ResponseEntity<Page<UmsApiLink>> getAllApiLinks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "resourceCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String method) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, API_LINK_SORT_FIELDS,
                "resourceCode");
        return ResponseEntity.ok(apiLinkRepository.listApiLinks(blankToNull(keyword), blankToNull(method),
                pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/all")
    public ResponseEntity<List<UmsApiLink>> getAllActiveApiLinks() {
        return ResponseEntity.ok(apiLinkRepository.findByIsActiveTrue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsApiLink> getApiLinkById(@PathVariable Long id) {
        return apiLinkRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_ADD)
    public ResponseEntity<UmsApiLink> createApiLink(@Valid @RequestBody UmsApiLink apiLink) {
        // SEC-01: a create never merges onto an existing row, whatever id the body carried.
        apiLink.setId(null);
        apiLink.setIsActive(true);
        return ResponseEntity.ok(apiLinkRepository.save(apiLink));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<UmsApiLink> updateApiLink(@PathVariable Long id, @Valid @RequestBody UmsApiLink apiLink) {
        return apiLinkRepository.findById(id)
                .map(existing -> {
                    existing.setResourceCode(apiLink.getResourceCode());
                    existing.setResourceName(apiLink.getResourceName());
                    existing.setResourceNameAr(apiLink.getResourceNameAr());
                    existing.setMethod(apiLink.getMethod());
                    existing.setUrlPattern(apiLink.getUrlPattern());
                    if (apiLink.getIsActive() != null) {
                        existing.setIsActive(apiLink.getIsActive());
                    }
                    return ResponseEntity.ok(apiLinkRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_DELETE)
    public ResponseEntity<Void> deleteApiLink(@PathVariable Long id) {
        apiLinkRepository.findById(id).ifPresent(apiLink -> {
            apiLink.setIsActive(false);
            apiLinkRepository.save(apiLink);
        });
        return ResponseEntity.noContent().build();
    }
}
