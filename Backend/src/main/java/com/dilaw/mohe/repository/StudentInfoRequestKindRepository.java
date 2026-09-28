package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentInfoRequestKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentInfoRequestKindRepository extends JpaRepository<StudentInfoRequestKind, Integer> {

    List<StudentInfoRequestKind> findAllByOrderBySortOrderAsc();
}
