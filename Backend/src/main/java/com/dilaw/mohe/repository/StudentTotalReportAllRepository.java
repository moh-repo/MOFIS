package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentTotalReportAll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentTotalReportAllRepository extends JpaRepository<StudentTotalReportAll, String> {
} 