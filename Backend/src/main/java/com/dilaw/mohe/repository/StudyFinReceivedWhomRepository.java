package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinReceivedWhom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyFinReceivedWhomRepository extends JpaRepository<StudyFinReceivedWhom, Integer>,
        JpaSpecificationExecutor<StudyFinReceivedWhom> {

    List<StudyFinReceivedWhom> findByStudentForDelegationId(Integer studentForDelegationId);

    // nextId() — "SELECT MAX(STUDY_RECEIVED_WHOM_ID) + 1" — is deliberately gone.
    // It handed a deleted recipient's id to the next one created, and two
    // approvals signed off together read the same maximum. The key comes from
    // MOHECORE.SEQ_STUDY_FIN_RECEIVED_WHOM now (V130, plan W6-09).
}