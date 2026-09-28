package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudentGraphNodeDto;
import com.dilaw.mohe.service.StudentGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Node tree for the student relationship graph page.
 *
 * <p>Returns the whole expandable structure in one call, with every lookup
 * already resolved to the requested language.
 */
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
@RestController
@RequestMapping("/api/reports/student-graph")
public class StudentGraphController {

    /**
     * Authorities that grant sight of money, mirroring
     * {@link MoheSecurityExpressions#REPORT_FINANCE}. Checked in code rather
     * than with @PreAuthorize because the finance branches are omitted from an
     * otherwise valid response instead of rejecting the whole request.
     */
    private static final Set<String> FINANCE_AUTHORITIES = Set.of(
            "REPORT_FINANCE.VIEW", "REPORT_FINANCE.EXPORT", "REPORTS.VIEW", "FINANCE.VIEW",
            "UMS_ADMIN.VIEW", "UMS.VIEW");

    @Autowired
    private StudentGraphService studentGraphService;

    @GetMapping("/{studentId}")
    public ResponseEntity<StudentGraphNodeDto> graph(
            @PathVariable Integer studentId,
            @RequestParam(defaultValue = "ar") String lang) {

        StudentGraphNodeDto root = studentGraphService.buildGraph(studentId, lang, canSeeFinance());
        StudentGraphService.assignIds(root);
        return ResponseEntity.ok(root);
    }

    private static boolean canSeeFinance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (FINANCE_AUTHORITIES.contains(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
