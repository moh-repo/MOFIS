package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentCountry;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.repository.StudentCountryRepository;
import com.dilaw.mohe.repository.StudentGovMudRepository;
import com.dilaw.mohe.repository.StudentGovRepository;
import com.dilaw.mohe.util.LookupNameUniqueness;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentCountryService {

    @Autowired
    private StudentCountryRepository studentCountryRepository;

    /** Children of a country, for the delete guard (W6-06). */
    @Autowired
    private StudentGovRepository studentGovRepository;

    @Autowired
    private StudentGovMudRepository studentGovMudRepository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudentCountry> getStudentCountriesAll() {
        return studentCountryRepository.findAll();
    }

    /**
     * @throws ResponseStatusException 404 when no country has this id (live QA L4: a
     *         deleted country answered 200 with an empty body; every other lookup's
     *         read answers 404)
     */
    public StudentCountry getStudentCountryById(int id) {
        return studentCountryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Student Country not found"));
    }

    @Transactional
    public StudentCountry createStudentCountry(StudentCountry studentCountry) {
        LookupNameUniqueness.require(em, StudentCountry.class, "studentCountryName",
                studentCountry.getStudentCountryName(), "studentCountryId", null);
        studentCountry.setStudentCountryId(studentCountryRepository.nextId());
        return studentCountryRepository.save(studentCountry);
    }

    @Transactional
    public StudentCountry updateStudentCountry(Integer id, StudentCountry studentCountry) {
        Optional<StudentCountry> studentCountryOptional = studentCountryRepository.findById(id);
        if (studentCountryOptional.isEmpty()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Student Country not found");
        }
        StudentCountry existing = studentCountryOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudentCountryName(), studentCountry.getStudentCountryName())) {
            LookupNameUniqueness.require(em, StudentCountry.class, "studentCountryName",
                    studentCountry.getStudentCountryName(), "studentCountryId", id);
        }

        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name dropped the English name, the order and the Active
        // flag while the screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setStudentCountryName(studentCountry.getStudentCountryName());
        existing.setStudentCountryNameEn(studentCountry.getStudentCountryNameEn());
        if (studentCountry.getStudentCountryOrderNo() != null) {
            existing.setStudentCountryOrderNo(studentCountry.getStudentCountryOrderNo());
        }
        if (studentCountry.getIsActive() != null) existing.setIsActive(studentCountry.getIsActive());

        return studentCountryRepository.save(existing);
    }

    @Transactional
    /**
     * Refuses a delete that would orphan the governorates or municipalities
     * under this country (plan W6-06, QA C9). V129's foreign keys would refuse
     * it too, as a constraint violation the caller reads as a 500; counted here
     * it is a 409 that names what is in the way.
     */
    public void deleteStudentCountry(Integer id) {
        if (!studentCountryRepository.existsById(id)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Student Country not found");
        }
        int children = studentGovRepository.findByStudentCountryId(id).size()
                + studentGovMudRepository.findByStudentCountryId(id).size();
        if (children > 0) {
            throw new LocalizedStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "This country still has " + children + " governorate(s) or municipalit(ies); "
                            + "move or remove them first.",
                    "location.country.hasChildren", children);
        }
        studentCountryRepository.deleteById(id);
    }

}
