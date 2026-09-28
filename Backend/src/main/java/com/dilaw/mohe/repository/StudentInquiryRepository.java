package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentInquiryRepository extends JpaRepository<StudentInquiry, Integer> {

    List<StudentInquiry> findByStudentIdOrderByCreatedAtDesc(Integer studentId, Pageable pageable);

    Page<StudentInquiry> findByStudentId(Integer studentId, Pageable pageable);

    Optional<StudentInquiry> findByIdAndStudentId(Integer id, Integer studentId);
}
