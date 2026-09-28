package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentTotalReportAll;
import com.dilaw.mohe.repository.StudentTotalReportAllRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentTotalReportAllService {

    @Autowired
    private StudentTotalReportAllRepository repository;

    public List<StudentTotalReportAll> getAll() {
        return repository.findAll();
    }

    public StudentTotalReportAll getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentTotalReportAll not found"));
    }

    @Transactional
    public StudentTotalReportAll create(StudentTotalReportAll entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentTotalReportAll update(String id, StudentTotalReportAll entity) {
        Optional<StudentTotalReportAll> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentTotalReportAll not found");
        }
        entity.setStudentTotalReportName(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the caller read success. */
    @Transactional
    public void delete(String id) {
        if (id == null || !repository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Report row not found");
        }
        repository.deleteById(id);
    }
} 