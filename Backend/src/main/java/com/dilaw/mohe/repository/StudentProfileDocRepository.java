package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentProfileDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentProfileDocRepository extends JpaRepository<StudentProfileDoc, Integer> {
}
