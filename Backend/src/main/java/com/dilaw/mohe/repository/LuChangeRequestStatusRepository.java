package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuChangeRequestStatusRepository extends JpaRepository<LuChangeRequestStatus, Integer> {

    /** Live rows in the order the lists are shown in; IS_DELETED is this table's delete. */
    List<LuChangeRequestStatus> findByIsDeletedFalseOrderBySortOrderAsc();
}
