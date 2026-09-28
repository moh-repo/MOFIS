package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.service.UmsPageService;
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
@RequestMapping("api/v1/ums/pages")
@PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_VIEW)
public class UmsPageController {

    private static final Set<String> PAGE_SORT_FIELDS = Set.of(
            "id", "resourceCode", "resourceName", "displayOrder", "route",
            "isVisible", "isActive", "createdAt");

    @Autowired
    private UmsPageService pageService;

    // ========== Page CRUD ==========

    @GetMapping("/")
    public ResponseEntity<Page<UmsPage>> getAllPages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean visible) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, PAGE_SORT_FIELDS, "displayOrder");
        return ResponseEntity.ok(pageService.listPages(blankToNull(keyword), visible, pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/all")
    public ResponseEntity<List<UmsPage>> getAllActivePages() {
        return ResponseEntity.ok(pageService.getAllActivePages());
    }

    @GetMapping("/visible")
    public ResponseEntity<List<UmsPage>> getVisiblePages() {
        return ResponseEntity.ok(pageService.getVisiblePages());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsPage> getPageById(@PathVariable Long id) {
        return pageService.getPageById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<UmsPage> getPageByCode(@PathVariable String code) {
        return pageService.getPageByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_ADD)
    public ResponseEntity<UmsPage> createPage(@Valid @RequestBody UmsPage page) {
        return ResponseEntity.ok(pageService.createPage(page));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<UmsPage> updatePage(@PathVariable Long id, @Valid @RequestBody UmsPage page) {
        return ResponseEntity.ok(pageService.updatePage(id, page));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_DELETE)
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        pageService.deletePage(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Sections ==========

    @GetMapping("/{pageId}/sections")
    public ResponseEntity<List<UmsPageSection>> getPageSections(@PathVariable Long pageId) {
        return ResponseEntity.ok(pageService.getPageSections(pageId));
    }

    @PostMapping("/sections")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_ADD)
    public ResponseEntity<UmsPageSection> createSection(@Valid @RequestBody UmsPageSection section) {
        return ResponseEntity.ok(pageService.createSection(section));
    }

    @PutMapping("/sections/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<UmsPageSection> updateSection(@PathVariable Long id, @Valid @RequestBody UmsPageSection section) {
        return ResponseEntity.ok(pageService.updateSection(id, section));
    }

    // ========== Fields ==========

    @GetMapping("/{pageId}/fields")
    public ResponseEntity<List<UmsField>> getPageFields(@PathVariable Long pageId) {
        return ResponseEntity.ok(pageService.getPageFields(pageId));
    }

    @PostMapping("/fields")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_ADD)
    public ResponseEntity<UmsField> createField(@Valid @RequestBody UmsField field) {
        return ResponseEntity.ok(pageService.createField(field));
    }

    @PutMapping("/fields/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<UmsField> updateField(@PathVariable Long id, @Valid @RequestBody UmsField field) {
        return ResponseEntity.ok(pageService.updateField(id, field));
    }

    @DeleteMapping("/fields/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_DELETE)
    public ResponseEntity<Void> deleteField(@PathVariable Long id) {
        pageService.deleteField(id);
        return ResponseEntity.noContent().build();
    }
}
