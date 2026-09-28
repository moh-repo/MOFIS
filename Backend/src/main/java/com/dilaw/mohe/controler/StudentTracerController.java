package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.NetworkGraphDto;
import com.dilaw.mohe.dto.TracerFilterDto;
import com.dilaw.mohe.model.EmploymentHistory;
import com.dilaw.mohe.repository.EmploymentHistoryRepository;
import com.dilaw.mohe.service.StudentTracerService;
import com.dilaw.mohe.service.TracerEmploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tracer")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
@RequiredArgsConstructor
public class StudentTracerController {

    private final StudentTracerService service;
    private final EmploymentHistoryRepository employmentHistoryRepository;
    private final TracerEmploymentService employmentService;

    @PostMapping("/network")
    public ResponseEntity<NetworkGraphDto> getNetworkGraph(@RequestBody TracerFilterDto filter) {
        return ResponseEntity.ok(service.getNetworkGraph(filter));
    }

    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, List<String>>> getFilterOptions(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getFilterOptions(countryId));
    }

    @GetMapping("/nodes/{nodeId}/employment")
    public ResponseEntity<List<EmploymentHistory>> getEmploymentHistory(@PathVariable Integer nodeId) {
        return ResponseEntity.ok(employmentService.byNode(nodeId));
    }

    @PostMapping("/nodes/{nodeId}/employment")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<EmploymentHistory> addEmployment(
            @PathVariable Integer nodeId,
            @RequestBody EmploymentHistory employment) {
        return ResponseEntity.ok(employmentService.add(nodeId, employment));
    }

    @PutMapping("/employment/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<EmploymentHistory> updateEmployment(
            @PathVariable Integer id,
            @RequestBody EmploymentHistory employment) {
        return ResponseEntity.ok(employmentService.update(id, employment));
    }

    @DeleteMapping("/employment/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<Void> deleteEmployment(@PathVariable Integer id) {
        employmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employment/companies")
    public ResponseEntity<List<String>> getDistinctCompanies() {
        return ResponseEntity.ok(employmentHistoryRepository.findDistinctCompanies());
    }

    @GetMapping("/employment/industries")
    public ResponseEntity<List<String>> getDistinctIndustries() {
        return ResponseEntity.ok(employmentHistoryRepository.findDistinctIndustries());
    }
}