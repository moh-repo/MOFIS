package com.dilaw.mohe.model;

import jakarta.persistence.PrePersist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live test L1 (23 Sep 2026): a PUT whose body did not mention {@code isActive}
 * switched a disabled row back on. The legacy lookup entities initialised the
 * field to {@code true}, so an omitted flag bound as an explicit one and the
 * services' null guards never fired. The default now belongs to the insert alone.
 */
class LookupIsActiveDefaultTest {

    /** Every legacy lookup entity that carried the initialiser. */
    private static final List<Class<?>> LOOKUPS = List.of(
            MoheMain.class, MoheSubMain.class, StudentAgencyType.class, StudentCountry.class,
            StudentCurrentLevel.class, StudentDegree.class, StudentGender.class, StudentMajor.class,
            StudentMinistryType.class, StudentStudyStatus.class, StudentStudyType.class,
            StudentUniversityType.class, StudyCollegeIn.class, StudyCountryIn.class, StudyCurrencyIn.class,
            StudyDegreeIn.class, StudyEductionIn.class, StudyGovIn.class, StudyMajorIn.class,
            StudyReceivedWhomStatus.class, StudyUniversityIn.class);

    @Test
    @DisplayName("a row bound from a body that omits the flag states nothing about it")
    void freshRowStatesNothing() {
        assertThat(new StudentDegree().getIsActive()).isNull();
        assertThat(new MoheMain().getIsActive()).isNull();
        assertThat(new StudyCountryIn().getIsActive()).isNull();
        assertThat(new StudentCountry().getIsActive()).isNull();
        assertThat(new StudentGender().getIsActive()).isNull();
    }

    @Test
    @DisplayName("a new row is active unless the body said otherwise")
    void insertDefaultsToActive() {
        StudentDegree degree = new StudentDegree();
        degree.defaultActive();
        assertThat(degree.getIsActive()).isTrue();

        MoheMain main = new MoheMain();
        main.defaultActive();
        assertThat(main.getIsActive()).isTrue();

        StudentCountry country = new StudentCountry();
        country.defaultActive();
        assertThat(country.getIsActive()).isTrue();

        StudyCountryIn disabled = new StudyCountryIn();
        disabled.setIsActive(false);
        disabled.defaultActive();
        assertThat(disabled.getIsActive()).as("an explicit false is kept").isFalse();
    }

    @Test
    @DisplayName("every legacy lookup entity defaults on insert, and only then")
    void everyLookupDefaultsOnPersist() throws Exception {
        for (Class<?> entity : LOOKUPS) {
            Object row = entity.getDeclaredConstructor().newInstance();
            assertThat(entity.getMethod("getIsActive").invoke(row)).as(entity.getSimpleName() + " when new").isNull();

            Method defaultActive = entity.getDeclaredMethod("defaultActive");
            assertThat(defaultActive.isAnnotationPresent(PrePersist.class))
                    .as(entity.getSimpleName() + ".defaultActive is @PrePersist").isTrue();
            defaultActive.setAccessible(true);
            defaultActive.invoke(row);
            assertThat(entity.getMethod("getIsActive").invoke(row)).as(entity.getSimpleName() + " after insert").isEqualTo(true);
        }
    }
}
