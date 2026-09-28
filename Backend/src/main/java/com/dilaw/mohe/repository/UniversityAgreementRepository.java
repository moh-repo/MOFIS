package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.UniversityAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UniversityAgreementRepository extends JpaRepository<UniversityAgreement, Long> {

    /*
     * The by-agreement-status / by-operational-status queries are gone with V075:
     * no agreement is recorded, so those columns were literals ('active',
     * 'operational') and the filters matched everything or nothing. Ordered by
     * the students covered — the one figure the view really has.
     */
    @Query("SELECT u FROM UniversityAgreement u WHERE " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.totalStudentsUnderAgreement DESC")
    List<UniversityAgreement> findAllOrderedByStudentsCovered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT u FROM UniversityAgreement u WHERE u.countryName = :countryName AND " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.totalStudentsUnderAgreement DESC")
    List<UniversityAgreement> findByCountry(
            @Param("countryName") String countryName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
