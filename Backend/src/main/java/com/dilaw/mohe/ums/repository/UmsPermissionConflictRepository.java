package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsPermissionConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UmsPermissionConflictRepository extends JpaRepository<UmsPermissionConflict, Long> {

    List<UmsPermissionConflict> findByIsActiveTrue();
}
