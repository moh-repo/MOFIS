package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuNotificationRecipientType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuNotificationRecipientTypeRepository extends JpaRepository<LuNotificationRecipientType, Integer> {

    /** Live rows in the order the lists are shown in; IS_DELETED is this table's delete. */
    List<LuNotificationRecipientType> findByIsDeletedFalseOrderBySortOrderAsc();
}
