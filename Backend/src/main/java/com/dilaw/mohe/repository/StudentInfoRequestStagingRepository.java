package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentInfoRequestStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentInfoRequestStagingRepository extends JpaRepository<StudentInfoRequestStaging, Integer> {
}
