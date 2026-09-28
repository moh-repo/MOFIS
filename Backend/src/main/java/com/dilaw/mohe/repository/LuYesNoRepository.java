package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuYesNo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuYesNoRepository extends JpaRepository<LuYesNo, Integer> {
}
