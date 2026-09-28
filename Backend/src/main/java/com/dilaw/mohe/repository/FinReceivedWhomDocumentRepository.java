package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.FinReceivedWhomDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinReceivedWhomDocumentRepository extends JpaRepository<FinReceivedWhomDocument, Integer> {

    List<FinReceivedWhomDocument> findByWhomIdAndIsActiveTrueOrderByUploadDateDesc(Integer whomId);

    /** How many finance documents claim this type; by name, as there is no FK. */
    long countByDocumentType(String documentType);

    /** Every document of a recipient, active or not — what a delete has to take with it. */
    List<FinReceivedWhomDocument> findByWhomId(Integer whomId);

    void deleteByWhomId(Integer whomId);
}
