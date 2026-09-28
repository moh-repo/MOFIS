package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyCurrencyIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyCurrencyInRepository extends JpaRepository<StudyCurrencyIn, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDY_CURRENCY_IN_ID), 0) + 1 FROM MOHECORE.STUDY_CURRENCY_IN", nativeQuery = true)
    int nextId();

    /**
     * The ministry's own currency — what its disbursement vouchers are denominated in.
     * A filtered unique index guarantees at most one row, so this is a lookup rather
     * than a choice; empty only if nobody has flagged one.
     */
    java.util.Optional<StudyCurrencyIn> findFirstByIsFunctionalTrue();
}
