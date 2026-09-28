package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentListDetailed;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class IndividualStudentReportService {

    @Autowired
    private StudentListDetailedService studentListDetailedService;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    public StudentListDetailed getStudentForReport(Integer studentId) {
        if (studentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required");
        }
        StudentListDetailed student = studentListDetailedService.getById(studentId);
        if (student == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        // The same rule as the student record (N14); the check below it let a null country through.
        studentAccessGuard.requireStudent(studentId);
        Integer studyCountryIn = studentDataRepository.findStudyCountryInByStudentId(studentId).orElse(null);
        if (studyCountryIn != null && !countryScopeService.canAccessCountry(studyCountryIn)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        }
        return student;
    }
}
