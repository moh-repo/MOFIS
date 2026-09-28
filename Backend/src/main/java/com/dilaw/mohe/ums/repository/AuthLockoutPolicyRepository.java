package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.AuthLockoutPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthLockoutPolicyRepository extends JpaRepository<AuthLockoutPolicy, Integer> {

    /** The active rungs, in the order they are climbed. */
    List<AuthLockoutPolicy> findByIsActiveTrueOrderByStageOrderAsc();
}
