package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuTravelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuTravelTypeRepository extends JpaRepository<LuTravelType, Integer> {

    List<LuTravelType> findAllByActiveTrueOrderBySortOrderAsc();
}
