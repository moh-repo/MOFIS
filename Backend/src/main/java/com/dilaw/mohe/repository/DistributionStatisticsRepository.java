package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.DistributionStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistributionStatisticsRepository extends JpaRepository<DistributionStatistics, DistributionStatistics.DistributionStatisticsId> {
    
    @Query("SELECT d FROM DistributionStatistics d WHERE d.distributionType = :distributionType ORDER BY d.studentCount DESC")
    List<DistributionStatistics> findByDistributionType(@Param("distributionType") String distributionType);
    
    @Query("SELECT d FROM DistributionStatistics d WHERE d.categorySignificance = :significance ORDER BY d.studentCount DESC")
    List<DistributionStatistics> findByCategorySignificance(@Param("significance") String significance);
    
    @Query("SELECT d FROM DistributionStatistics d WHERE d.percentageOfTotal >= :minPercentage ORDER BY d.percentageOfTotal DESC")
    List<DistributionStatistics> findByMinimumPercentage(@Param("minPercentage") java.math.BigDecimal minPercentage);
    
    @Query("SELECT d FROM DistributionStatistics d WHERE d.rankPosition <= :maxRank ORDER BY d.rankPosition")
    List<DistributionStatistics> findTopRanked(@Param("maxRank") Long maxRank);
    
    @Query("SELECT DISTINCT d.distributionType FROM DistributionStatistics d ORDER BY d.distributionType")
    List<String> findAllDistributionTypes();

    @Query(value = """
            SELECT distribution_type, category_name, category_name_en, student_count, percentage_of_total, rank_position, category_significance
            FROM (
                SELECT distribution_type, category_name, category_name_en, student_count,
                       CAST(student_count * 100.0 / SUM(student_count) OVER (PARTITION BY distribution_type) AS DECIMAL(5,2)) AS percentage_of_total,
                       RANK() OVER (PARTITION BY distribution_type ORDER BY student_count DESC) AS rank_position,
                       -- Thresholds come from LU_TREND_CLASS (V028) rather than
                       -- being literals in this string. The cut-off between a
                       -- "major" and a "minor" concentration is a ministry
                       -- reporting decision, so it belongs in a table someone
                       -- can change without a redeploy.
                       (SELECT TOP 1 LOWER(tc.CODE)
                          FROM MOHECORE.LU_TREND_CLASS tc
                         WHERE tc.CATEGORY = 'DISTRIBUTION_SIGNIFICANCE'
                           AND tc.IS_DELETED = 0
                           AND student_count >= tc.THRESHOLD_MIN
                         ORDER BY tc.THRESHOLD_MIN DESC) AS category_significance
                FROM (
                    SELECT 'country' AS distribution_type,
                           ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'غير محدد') AS category_name,
                           ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'Unspecified') AS category_name_en,
                           COUNT(*) AS student_count
                    FROM MOHECORE.STUDENT_DATA sd
                    LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                    GROUP BY sci.STUDY_COUNTRY_IN_NAME, sci.STUDY_COUNTRY_IN_NAME_EN
                    UNION ALL
                    SELECT 'major',
                           ISNULL(sm.STUDENT_MAJOR_NAME, N'غير محدد'),
                           ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'Unspecified'),
                           COUNT(*)
                    FROM MOHECORE.STUDENT_DATA sd
                    LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                    GROUP BY sm.STUDENT_MAJOR_NAME, sm.STUDENT_MAJOR_NAME_EN
                    UNION ALL
                    SELECT 'degree',
                           ISNULL(sdeg.STUDENT_DEGREE_NAME, N'غير محدد'),
                           ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'Unspecified'),
                           COUNT(*)
                    FROM MOHECORE.STUDENT_DATA sd
                    LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                    GROUP BY sdeg.STUDENT_DEGREE_NAME, sdeg.STUDENT_DEGREE_NAME_EN
                    UNION ALL
                    SELECT 'status',
                           ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'غير محدد'),
                           ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'Unspecified'),
                           COUNT(*)
                    FROM MOHECORE.STUDENT_DATA sd
                    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                    GROUP BY sss.STUDENT_STUDY_STATUS_NAME, sss.STUDENT_STUDY_STATUS_NAME_EN
                ) base
            ) ranked
            WHERE (:distributionType IS NULL OR distribution_type = :distributionType)
              AND (:significance IS NULL OR category_significance = :significance)
              AND (:minPercentage IS NULL OR percentage_of_total >= :minPercentage)
              AND (:maxRank IS NULL OR rank_position <= :maxRank)
            ORDER BY distribution_type, student_count DESC
            """, nativeQuery = true)
    List<DistributionStatistics> search(@Param("applyCountryFilter") boolean applyCountryFilter,
                                        @Param("countryIds") List<Integer> countryIds,
                                        @Param("distributionType") String distributionType,
                                        @Param("significance") String significance,
                                        @Param("minPercentage") java.math.BigDecimal minPercentage,
                                        @Param("maxRank") Long maxRank);
}

