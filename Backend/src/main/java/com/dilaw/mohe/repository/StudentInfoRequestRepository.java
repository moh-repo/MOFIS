package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentInfoRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StudentInfoRequestRepository extends JpaRepository<StudentInfoRequest, Integer>,
        JpaSpecificationExecutor<StudentInfoRequest> {

    Page<StudentInfoRequest> findByStudentIdOrderByCreatedAtDesc(Integer studentId, Pageable pageable);

    @Query("SELECT r FROM StudentInfoRequest r WHERE r.studentId = :studentId AND (:status IS NULL OR r.status = :status) AND (:requestType IS NULL OR r.requestType = :requestType) ORDER BY r.createdAt DESC")
    Page<StudentInfoRequest> findByStudentIdAndFilters(@Param("studentId") Integer studentId,
                                                       @Param("status") String status,
                                                       @Param("requestType") String requestType,
                                                       Pageable pageable);

    Optional<StudentInfoRequest> findByIdAndStudentId(Integer id, Integer studentId);

    @Query(value = "SELECT r FROM StudentInfoRequest r WHERE " +
            "(:createdBy IS NULL OR r.createdBy = :createdBy) AND " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:requestType IS NULL OR r.requestType = :requestType) AND " +
            "(:studentId IS NULL OR r.studentId = :studentId) AND " +
            "(:dateFrom IS NULL OR r.createdAt >= :dateFrom) AND " +
            "(:dateTo IS NULL OR r.createdAt <= :dateTo)",
            countQuery = "SELECT COUNT(r) FROM StudentInfoRequest r WHERE " +
            "(:createdBy IS NULL OR r.createdBy = :createdBy) AND " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:requestType IS NULL OR r.requestType = :requestType) AND " +
            "(:studentId IS NULL OR r.studentId = :studentId) AND " +
            "(:dateFrom IS NULL OR r.createdAt >= :dateFrom) AND " +
            "(:dateTo IS NULL OR r.createdAt <= :dateTo)")
    Page<StudentInfoRequest> findWithFilters(@Param("createdBy") String createdBy,
                                             @Param("status") String status,
                                             @Param("requestType") String requestType,
                                             @Param("studentId") Integer studentId,
                                             @Param("dateFrom") Instant dateFrom,
                                             @Param("dateTo") Instant dateTo,
                                             Pageable pageable);

    Page<StudentInfoRequest> findByStudentId(Integer studentId, Pageable pageable);
}
