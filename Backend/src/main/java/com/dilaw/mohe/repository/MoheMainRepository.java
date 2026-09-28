package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.MoheMain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MoheMainRepository extends JpaRepository<MoheMain, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(MOHE_MAIN_ID), 0) + 1 FROM MOHECORE.MOHE_MAIN", nativeQuery = true)
    int nextId();
}
