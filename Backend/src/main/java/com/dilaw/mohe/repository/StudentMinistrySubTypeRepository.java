package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentMinistrySubType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentMinistrySubTypeRepository extends JpaRepository<StudentMinistrySubType, Integer> {
} 