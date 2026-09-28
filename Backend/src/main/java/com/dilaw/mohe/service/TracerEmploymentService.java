package com.dilaw.mohe.service;

import com.dilaw.mohe.model.EmploymentHistory;
import com.dilaw.mohe.model.StudentNode;
import com.dilaw.mohe.repository.EmploymentHistoryRepository;
import com.dilaw.mohe.repository.StudentNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * A tracer node's employment history, for a student the caller may reach
 * (FIX-PLAN N2, 25 Sep 2026).
 *
 * <p>The controller read and wrote {@code MOHENTracer.EMPLOYMENT_HISTORY} straight
 * through its repository. Those tables carry no country, so row-level security
 * never covers them, and any node id — a small sequential integer — reached
 * another attaché's graduate. A node belongs to exactly one student
 * ({@link StudentNode#getStudentId()}); every call here resolves it and applies
 * {@link StudentAccessGuard}, the rule every other per-student endpoint uses.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TracerEmploymentService {

    private final StudentNodeRepository studentNodeRepository;
    private final EmploymentHistoryRepository employmentHistoryRepository;
    private final StudentAccessGuard studentAccessGuard;

    public List<EmploymentHistory> byNode(Integer nodeId) {
        requireNode(nodeId);
        return employmentHistoryRepository.findByNodeId(nodeId);
    }

    @Transactional
    public EmploymentHistory add(Integer nodeId, EmploymentHistory employment) {
        requireNode(nodeId);
        employment.setEmploymentId(null);
        employment.setNodeId(nodeId);
        return employmentHistoryRepository.save(employment);
    }

    /** The row stays on its own node: the body cannot move it to another student. */
    @Transactional
    public EmploymentHistory update(Integer id, EmploymentHistory employment) {
        EmploymentHistory existing = requireEmployment(id);
        employment.setEmploymentId(id);
        employment.setNodeId(existing.getNodeId());
        return employmentHistoryRepository.save(employment);
    }

    @Transactional
    public void delete(Integer id) {
        requireEmployment(id);
        employmentHistoryRepository.deleteById(id);
    }

    private EmploymentHistory requireEmployment(Integer id) {
        EmploymentHistory existing = employmentHistoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment record not found"));
        requireNode(existing.getNodeId());
        return existing;
    }

    private void requireNode(Integer nodeId) {
        StudentNode node = studentNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracer node not found"));
        studentAccessGuard.requireStudent(node.getStudentId());
    }
}
