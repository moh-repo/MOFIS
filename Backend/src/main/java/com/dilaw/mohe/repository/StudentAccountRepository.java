package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentAccountRepository extends JpaRepository<StudentAccount, Integer> {

    /** Distinct study country IDs present in STUDENT_ACCOUNT (excluding null/zero). */
    @Query("SELECT DISTINCT sa.studyCountryInId FROM StudentAccount sa WHERE sa.studyCountryInId IS NOT NULL AND sa.studyCountryInId <> 0")
    List<Integer> findDistinctStudyCountryInIds();
}
