package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuSemesterNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuSemesterNumberRepository extends JpaRepository<LuSemesterNumber, Integer> {
}
