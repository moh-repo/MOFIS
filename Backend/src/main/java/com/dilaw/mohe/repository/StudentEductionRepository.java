package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentEduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentEductionRepository extends JpaRepository<StudentEduction, Integer> {
} 