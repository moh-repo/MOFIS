package com.dilaw.mohe.report;

import com.dilaw.mohe.repository.AcademicYearReportRepository;
import com.dilaw.mohe.repository.CustomFilterReportRepository;
import com.dilaw.mohe.repository.CustomPeriodReportRepository;
import com.dilaw.mohe.repository.FiscalYearReportRepository;
import com.dilaw.mohe.repository.PredictiveAnalyticsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The five report entities read through their own SELECT, not a dead view.
 *
 * <p>Each mapped a {@code V_*} view whose columns no longer matched the entity
 * (62 columns adrift; {@code V_Predictive_Analytics} had {@code historical_avg}
 * where the entity read {@code historical_average}), so every read that was not
 * one of the repositories' hand-aliased native queries — {@code findAll()},
 * {@code findById()}, the JPQL finders — failed with "Invalid column name". They
 * are now {@code @Subselect} entities carrying the native SELECT, and V066 drops
 * the views. A {@code findAll()} that runs is the whole assertion: it is the read
 * that used to fail.
 */
@Tag("integration")
@SpringBootTest
class ReportEntitySubselectTest {

    @Autowired private FiscalYearReportRepository fiscalYear;
    @Autowired private CustomFilterReportRepository customFilter;
    @Autowired private AcademicYearReportRepository academicYear;
    @Autowired private CustomPeriodReportRepository customPeriod;
    @Autowired private PredictiveAnalyticsRepository predictive;

    @Test
    @DisplayName("fiscal year report: findAll runs")
    void fiscalYear() {
        assertThatCode(fiscalYear::findAll).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("custom filter report: findAll runs")
    void customFilter() {
        assertThatCode(customFilter::findAll).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("academic year report: findAll runs")
    void academicYear() {
        assertThatCode(academicYear::findAll).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("custom period report: findAll runs")
    void customPeriod() {
        assertThatCode(customPeriod::findAll).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("predictive analytics: findAll and the JPQL finders run (historical_average resolves)")
    void predictiveAnalytics() {
        assertThatCode(predictive::findAll).doesNotThrowAnyException();
        assertThatCode(() -> predictive.findByForecastCategory("finance_amount")).doesNotThrowAnyException();
        assertThatCode(predictive::findAllForecastCategories).doesNotThrowAnyException();
    }
}
