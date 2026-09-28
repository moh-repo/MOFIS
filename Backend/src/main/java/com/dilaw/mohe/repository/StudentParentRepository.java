package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentParent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentParentRepository extends JpaRepository<StudentParent, Integer> {

    List<StudentParent> findByStudentIdAndIsDeletedFalseOrderByParentIdAsc(Integer studentId);

    Optional<StudentParent> findByParentIdAndStudentIdAndIsDeletedFalse(Integer parentId, Integer studentId);
}
