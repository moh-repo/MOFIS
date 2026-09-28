package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface StudentChangeRequestRepository extends JpaRepository<StudentChangeRequest, Integer>,
        JpaSpecificationExecutor<StudentChangeRequest> {

    List<StudentChangeRequest> findByStudentIdAndIsDeletedFalseOrderByCreatedAtDesc(Integer studentId);

    List<StudentChangeRequest> findBySubmissionIdAndIsDeletedFalse(String submissionId);

    Optional<StudentChangeRequest> findByRequestIdAndStudentUserIdAndIsDeletedFalse(
            Integer requestId, Integer studentUserId);

    @Query("SELECT r FROM StudentChangeRequest r " +
           "WHERE r.isDeleted = false " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:studentId IS NULL OR r.studentId = :studentId)")
    Page<StudentChangeRequest> findAllWithFilters(
            @Param("status") String status,
            @Param("studentId") Integer studentId,
            Pageable pageable);

    @Query("SELECT r FROM StudentChangeRequest r " +
           "WHERE r.isDeleted = false " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:studentId IS NULL OR r.studentId = :studentId) " +
           "AND r.studentId IN :nameMatchIds")
    Page<StudentChangeRequest> findAllWithFiltersAndIds(
            @Param("status") String status,
            @Param("studentId") Integer studentId,
            @Param("nameMatchIds") Set<Integer> nameMatchIds,
            Pageable pageable);

    @Query("SELECT r FROM StudentChangeRequest r " +
           "WHERE r.studentId = :studentId " +
           "AND r.isDeleted = false " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    List<StudentChangeRequest> findByStudentIdAndStatus(
            @Param("studentId") Integer studentId,
            @Param("status") String status);
}
