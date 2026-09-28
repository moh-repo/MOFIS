package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UmsUserRepository extends JpaRepository<UmsUser, Long>, JpaSpecificationExecutor<UmsUser> {

    /** V121: whether the account still holds a password an administrator set. */
    @Query("SELECT u.mustChangePassword FROM UmsUser u WHERE u.id = :id")
    Optional<Boolean> findMustChangePassword(@Param("id") Long id);

    Optional<UmsUser> findByUserName(String userName);

    Optional<UmsUser> findByEmail(String email);

    /** Notifications sent to one employee by email (review R4); a list, since emails are not unique. */
    List<UmsUser> findByEmailIgnoreCaseAndIsDeletedFalse(String email);

    Optional<UmsUser> findByUserNameAndIsDeletedFalse(String userName);

    boolean existsByUserName(String userName);

    boolean existsByEmail(String email);

    Page<UmsUser> findByIsDeletedFalse(Pageable pageable);

    long countByIsDeletedFalse();

    long countByIsDeletedFalseAndCreatedAtAfter(LocalDateTime createdAfter);

    Page<UmsUser> findByIsDeletedFalseAndStatus(String status, Pageable pageable);

    Page<UmsUser> findByIsDeletedFalseAndIsAdminTrue(Pageable pageable);

        @Query("SELECT u FROM UmsUser u WHERE u.isDeleted = false AND " +
            "(LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<UmsUser> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT u FROM UmsUser u WHERE u.isDeleted = false " +
            "AND (:keyword IS NULL OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:privilege IS NULL OR :privilege = '' OR " +
            "(:privilege = 'ADMIN' AND (COALESCE(u.isAdmin, false) = true OR COALESCE(u.isSuperAdmin, false) = true)) OR " +
            "(:privilege = 'REGULAR' AND COALESCE(u.isAdmin, false) = false AND COALESCE(u.isSuperAdmin, false) = false))")
    Page<UmsUser> listUsers(@Param("keyword") String keyword, @Param("status") String status,
            @Param("privilege") String privilege, Pageable pageable);

    @Query("SELECT DISTINCT u FROM UmsUser u " +
            "LEFT JOIN u.userGroups ug " +
            "LEFT JOIN ug.group g " +
            "WHERE u.isDeleted = false AND g.id = :groupId")
    List<UmsUser> findUsersByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT DISTINCT u FROM UmsUser u " +
            "LEFT JOIN u.userGroups ug " +
            "LEFT JOIN ug.group g " +
            "WHERE u.isDeleted = false AND g.id IN :groupIds")
    List<UmsUser> findUsersByGroupIds(@Param("groupIds") List<Long> groupIds);

    @Query("SELECT u FROM UmsUser u " +
            "LEFT JOIN u.userCountries uc " +
            "WHERE u.isDeleted = false AND uc.countryId = :countryId AND uc.isActive = true")
    List<UmsUser> findUsersByCountryId(@Param("countryId") Integer countryId);

    /**
     * Notification recipients — an <b>intersection</b> rule, deliberately unlike
     * {@code StaffCountrySpec.administrableBy}, which uses a subset rule.
     *
     * <p>Anyone who serves my attaché should hear from me, so overlapping in a
     * single country is enough. Administering someone is the stricter question:
     * a colleague who also serves fifteen other attachés is not mine to edit.
     * The two are not versions of the same rule, and this one is not a bug to be
     * aligned with the other.
     */
    @Query("SELECT DISTINCT u FROM UmsUser u LEFT JOIN u.userCountries uc " +
            "WHERE u.isDeleted = false AND (u.isMinistryUser = true OR (uc.isActive = true AND uc.countryId IN :countryIds))")
    List<UmsUser> findEmployeesInCountryScope(@Param("countryIds") List<Integer> countryIds);

    /**
     * {@link #searchUsers} within {@link #findEmployeesInCountryScope}'s reach, in one
     * query, so the page size applies to the colleagues the caller may write to
     * (W4-09, review R4). Filtering after a 180-row search left a country officer
     * with nobody whenever the first 180 matches served other attachés.
     */
    @Query("SELECT DISTINCT u FROM UmsUser u LEFT JOIN u.userCountries uc " +
            "WHERE u.isDeleted = false AND (u.isMinistryUser = true OR (uc.isActive = true AND uc.countryId IN :countryIds)) " +
            "AND (LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<UmsUser> searchEmployeesInCountryScope(@Param("keyword") String keyword,
                                                @Param("countryIds") List<Integer> countryIds,
                                                Pageable pageable);

    /* Rows are [year, month, count]: the month as data, named by the client in its language. */
    @Query(value = "SELECT YEAR(u.CreatedAt) AS yr, MONTH(u.CreatedAt) AS mo, COUNT(*) AS cnt " +
            "FROM UMSCORE.Users u " +
            "WHERE u.IsDeleted = 0 AND u.CreatedAt >= :startDate " +
            "GROUP BY YEAR(u.CreatedAt), MONTH(u.CreatedAt) " +
            "ORDER BY YEAR(u.CreatedAt), MONTH(u.CreatedAt)", nativeQuery = true)
    List<Object[]> findUserRegistrationCountsByMonthSince(@Param("startDate") Instant startDate);

    /*
     * The dashboard's user counters for a country-scoped caller (V070, D3): the
     * accounts whose active UserCountries rows meet the caller's countries — an
     * intersection rule, like findEmployeesInCountryScope above, but WITHOUT its
     * ministry-user clause. A ministry user holds no UserCountries row, so a
     * Jordan admin's "registered users" never counts one; that is the intended
     * reading of "users of my countries". UMSCORE is outside the row-level
     * policy, so these queries are the only scope the counters get.
     */

    @Query("SELECT COUNT(DISTINCT u.id) FROM UmsUser u JOIN u.userCountries uc " +
            "WHERE u.isDeleted = false AND uc.isActive = true AND uc.countryId IN :countryIds")
    long countActiveInCountries(@Param("countryIds") List<Integer> countryIds);

    @Query("SELECT COUNT(DISTINCT u.id) FROM UmsUser u JOIN u.userCountries uc " +
            "WHERE u.isDeleted = false AND uc.isActive = true AND uc.countryId IN :countryIds " +
            "AND u.createdAt > :createdAfter")
    long countActiveInCountriesCreatedAfter(@Param("countryIds") List<Integer> countryIds,
            @Param("createdAfter") LocalDateTime createdAfter);

    @Query(value = "SELECT YEAR(u.CreatedAt) AS yr, MONTH(u.CreatedAt) AS mo, COUNT(DISTINCT u.Id) AS cnt " +
            "FROM UMSCORE.Users u " +
            "JOIN UMSCORE.UserCountries uc ON uc.UserId = u.Id AND uc.IsActive = 1 " +
            "WHERE u.IsDeleted = 0 AND u.CreatedAt >= :startDate AND uc.CountryId IN (:countryIds) " +
            "GROUP BY YEAR(u.CreatedAt), MONTH(u.CreatedAt) " +
            "ORDER BY YEAR(u.CreatedAt), MONTH(u.CreatedAt)", nativeQuery = true)
    List<Object[]> findUserRegistrationCountsByMonthSinceInCountries(@Param("startDate") Instant startDate,
            @Param("countryIds") List<Integer> countryIds);
}
