package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentCountryAll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentCountryAllRepository extends JpaRepository<StudentCountryAll, Integer> {
} 