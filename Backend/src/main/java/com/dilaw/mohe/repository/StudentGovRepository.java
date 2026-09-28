package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentGov;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentGovRepository extends JpaRepository<StudentGov, Integer> {

    /** Children of a country, for the delete guard (W6-06). */
    java.util.List<com.dilaw.mohe.model.StudentGov> findByStudentCountryId(Integer studentCountryId);
} 