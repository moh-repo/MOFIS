package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinanceBankBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StudyFinanceBankBranchRepository
        extends JpaRepository<StudyFinanceBankBranch, Integer>, JpaSpecificationExecutor<StudyFinanceBankBranch> {

    List<StudyFinanceBankBranch> findByStudyFinanceBankeId(Integer studyFinanceBankeId);

    long countByStudyFinanceBankeId(Integer studyFinanceBankeId);

    @Query(value = "SELECT ISNULL(MAX(STUDY_FINANCE_BANK_BRANCH_ID), 0) + 1 FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH", nativeQuery = true)
    int nextId();
}