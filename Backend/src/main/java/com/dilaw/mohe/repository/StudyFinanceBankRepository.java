package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinanceBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StudyFinanceBankRepository
        extends JpaRepository<StudyFinanceBank, Integer>, JpaSpecificationExecutor<StudyFinanceBank> {

    List<StudyFinanceBank> findByStudyCountryInId(Integer studyCountryInId);

    @Query(value = "SELECT ISNULL(MAX(STUDY_FINANCE_BANKE_ID), 0) + 1 FROM MOHECORE.STUDY_FINANCE_BANK", nativeQuery = true)
    int nextId();
}