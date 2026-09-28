package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentGovMud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentGovMudRepository extends JpaRepository<StudentGovMud, Integer> {
    List<StudentGovMud> findByStudentGovId(Integer studentGovId);
    List<StudentGovMud> findByStudentCountryId(Integer studentCountryId);
    List<StudentGovMud> findByStudentGovIdAndStudentCountryId(Integer studentGovId, Integer studentCountryId);
} 