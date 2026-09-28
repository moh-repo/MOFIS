package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentRelative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRelativeRepository extends JpaRepository<StudentRelative, Integer> {

    List<StudentRelative> findByStudentIdAndIsDeletedFalseOrderByRelativeIdAsc(Integer studentId);

    Optional<StudentRelative> findByRelativeIdAndStudentIdAndIsDeletedFalse(Integer relativeId, Integer studentId);

    boolean existsByStudentIdAndRelatedStudentIdAndRelationshipIdAndIsDeletedFalse(
            Integer studentId, Integer relatedStudentId, Integer relationshipId);
}
