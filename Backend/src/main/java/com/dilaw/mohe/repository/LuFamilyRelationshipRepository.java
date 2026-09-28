package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuFamilyRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuFamilyRelationshipRepository extends JpaRepository<LuFamilyRelationship, Integer> {

    List<LuFamilyRelationship> findAllByOrderBySortOrderAsc();
}
