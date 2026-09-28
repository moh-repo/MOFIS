package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuAcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuAcademicYearRepository extends JpaRepository<LuAcademicYear, Integer> {
}
