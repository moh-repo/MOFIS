package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentTravelTicket;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentTravelTicketRepository extends JpaRepository<StudentTravelTicket, Integer>,
        JpaSpecificationExecutor<StudentTravelTicket> {

    /** Country-scoped list with family members eager-loaded. */
    @EntityGraph(attributePaths = {"familyMembers"})
    @Override
    List<StudentTravelTicket> findAll(Specification<StudentTravelTicket> spec);

    @EntityGraph(attributePaths = {"familyMembers"})
    @Override
    List<StudentTravelTicket> findAll();

    @EntityGraph(attributePaths = {"familyMembers"})
    @Override
    Optional<StudentTravelTicket> findById(Integer id);

    @EntityGraph(attributePaths = {"familyMembers"})
    List<StudentTravelTicket> findByStudentId(Integer studentId);

    @EntityGraph(attributePaths = {"familyMembers"})
    @Query("SELECT t FROM StudentTravelTicket t WHERE t.studentId = :studentId ORDER BY t.travelDate DESC")
    List<StudentTravelTicket> findByStudentIdOrderByTravelDateDesc(@Param("studentId") Integer studentId);

    @EntityGraph(attributePaths = {"familyMembers"})
    @Query("SELECT t FROM StudentTravelTicket t WHERE t.travelType = :travelType")
    List<StudentTravelTicket> findByTravelType(@Param("travelType") String travelType);
}

