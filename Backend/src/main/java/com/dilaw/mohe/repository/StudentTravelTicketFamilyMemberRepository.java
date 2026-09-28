package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentTravelTicketFamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentTravelTicketFamilyMemberRepository extends JpaRepository<StudentTravelTicketFamilyMember, Integer> {

    List<StudentTravelTicketFamilyMember> findByTravelTicketId(Integer travelTicketId);

    @Query("SELECT f FROM StudentTravelTicketFamilyMember f WHERE f.travelTicketId = :travelTicketId ORDER BY f.birthdate DESC")
    List<StudentTravelTicketFamilyMember> findByTravelTicketIdOrderByBirthdateDesc(@Param("travelTicketId") Integer travelTicketId);
}

