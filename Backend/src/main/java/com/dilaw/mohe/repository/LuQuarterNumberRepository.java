package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuQuarterNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuQuarterNumberRepository extends JpaRepository<LuQuarterNumber, Integer> {

    /** Live rows in the order the lists are shown in; IS_DELETED is this table's delete. */
    List<LuQuarterNumber> findByIsDeletedFalseOrderBySortOrderAsc();
}
