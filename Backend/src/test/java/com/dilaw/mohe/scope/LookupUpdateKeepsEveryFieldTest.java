package com.dilaw.mohe.scope;

import com.dilaw.mohe.model.MoheSubMain;
import com.dilaw.mohe.model.StudentAgencyType;
import com.dilaw.mohe.model.StudentCountry;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudentUniversityType;
import com.dilaw.mohe.model.StudyDegreeIn;
import com.dilaw.mohe.service.MoheSubMainService;
import com.dilaw.mohe.service.StudentAgencyTypeService;
import com.dilaw.mohe.service.StudentCountryService;
import com.dilaw.mohe.service.StudentCurrentLevelService;
import com.dilaw.mohe.service.StudentDegreeService;
import com.dilaw.mohe.service.StudentUniversityTypeService;
import com.dilaw.mohe.service.StudyDegreeInService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Editing a System Coding row saves what the form shows, not only the Arabic name.
 *
 * <p>Seven services copied just the Arabic name onto the stored row, so the English
 * name, the Active flag and the order number (and the agency type, the main
 * category) were dropped while the screen said "saved" — the "Arabic saves, English
 * doesn't" of the QA CRUD matrix, confirmed live on 22 Sep 2026.
 * {@link LookupWriteRoundTripTest} checks only the Arabic name, which is how it hid.
 *
 * <p>Each case creates a marked row, edits it, reads it back and deletes it in a
 * {@code finally}; nothing that existed before the test is touched.
 */
@Tag("integration")
@SpringBootTest
class LookupUpdateKeepsEveryFieldTest {

    @Autowired private StudyDegreeInService studyDegrees;
    @Autowired private StudentUniversityTypeService universityTypes;
    @Autowired private StudentCountryService countries;
    @Autowired private StudentDegreeService degrees;
    @Autowired private StudentCurrentLevelService levels;
    @Autowired private StudentAgencyTypeService agencyTypes;
    @Autowired private MoheSubMainService subMains;
    @Autowired private JdbcTemplate jdbc;

    private static String marker() {
        return "UF-TEST " + UUID.randomUUID();
    }

    private static void withCleanup(Integer id, Consumer<Integer> delete, Runnable body) {
        try {
            body.run();
        } finally {
            try { delete.accept(id); } catch (RuntimeException ignored) { /* best-effort */ }
        }
    }

    @Test
    @DisplayName("study degree: English name and Active flag persist")
    void studyDegreeIn() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudyDegreeIn row = new StudyDegreeIn();
            row.setStudyDegreeInName(marker());
            Integer id = studyDegrees.create(row).getId();
            withCleanup(id, studyDegrees::delete, () -> {
                StudyDegreeIn edit = new StudyDegreeIn();
                edit.setStudyDegreeInName("AR " + id);
                edit.setStudyDegreeInNameEn("EN " + id);
                edit.setIsActive(false);
                studyDegrees.update(id, edit);

                StudyDegreeIn back = studyDegrees.getById(id);
                assertThat(back.getStudyDegreeInNameEn()).isEqualTo("EN " + id);
                assertThat(back.getIsActive()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("university type: English name, Active flag and agency type persist")
    void universityType() {
        Integer agency = jdbc.queryForObject(
                "SELECT MIN(STUDENT_AGENCY_TYPE_ID) FROM MOHECORE.STUDENT_AGENCY_TYPE", Integer.class);
        Integer otherAgency = jdbc.queryForObject(
                "SELECT MAX(STUDENT_AGENCY_TYPE_ID) FROM MOHECORE.STUDENT_AGENCY_TYPE", Integer.class);
        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentUniversityType row = new StudentUniversityType();
            row.setStudentUniversityTypeName(marker());
            row.setStudentAgencyTypeId(agency);
            Integer id = universityTypes.create(row).getId();
            withCleanup(id, universityTypes::delete, () -> {
                StudentUniversityType edit = new StudentUniversityType();
                edit.setStudentUniversityTypeName("AR " + id);
                edit.setStudentUniversityTypeNameEn("EN " + id);
                edit.setIsActive(false);
                edit.setStudentAgencyTypeId(otherAgency);
                universityTypes.update(id, edit);

                StudentUniversityType back = universityTypes.getById(id);
                assertThat(back.getStudentUniversityTypeNameEn()).isEqualTo("EN " + id);
                assertThat(back.getIsActive()).isFalse();
                assertThat(back.getStudentAgencyTypeId()).isEqualTo(otherAgency);
            });
        });
    }

    @Test
    @DisplayName("country: English name, order and Active flag persist")
    void country() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentCountry row = new StudentCountry();
            row.setStudentCountryName(marker());
            Integer id = countries.createStudentCountry(row).getStudentCountryId();
            withCleanup(id, countries::deleteStudentCountry, () -> {
                StudentCountry edit = new StudentCountry();
                edit.setStudentCountryName("AR " + id);
                edit.setStudentCountryNameEn("EN " + id);
                edit.setStudentCountryOrderNo(77);
                edit.setIsActive(false);
                countries.updateStudentCountry(id, edit);

                StudentCountry back = countries.getStudentCountryById(id);
                assertThat(back.getStudentCountryNameEn()).isEqualTo("EN " + id);
                assertThat(back.getStudentCountryOrderNo()).isEqualTo(77);
                assertThat(back.getIsActive()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("student degree: English name and Active flag persist")
    void studentDegree() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentDegree row = new StudentDegree();
            row.setStudentDegreeName(marker());
            Integer id = degrees.create(row).getId();
            withCleanup(id, degrees::delete, () -> {
                StudentDegree edit = new StudentDegree();
                edit.setStudentDegreeName("AR " + id);
                edit.setStudentDegreeNameEn("EN " + id);
                edit.setIsActive(false);
                degrees.update(id, edit);

                StudentDegree back = degrees.getById(id);
                assertThat(back.getStudentDegreeNameEn()).isEqualTo("EN " + id);
                assertThat(back.getIsActive()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("current level: English name, order and Active flag persist")
    void currentLevel() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentCurrentLevel row = new StudentCurrentLevel();
            row.setStudentCurrentLevelName(marker());
            Integer id = levels.create(row).getId();
            withCleanup(id, levels::delete, () -> {
                StudentCurrentLevel edit = new StudentCurrentLevel();
                edit.setStudentCurrentLevelName("AR " + id);
                edit.setStudentCurrentLevelNameEn("EN " + id);
                edit.setStudentCurrentLevelOrderNo(77);
                edit.setIsActive(false);
                levels.update(id, edit);

                StudentCurrentLevel back = levels.getById(id);
                assertThat(back.getStudentCurrentLevelNameEn()).isEqualTo("EN " + id);
                assertThat(back.getStudentCurrentLevelOrderNo()).isEqualTo(77);
                assertThat(back.getIsActive()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("agency type: English name, order and Active flag persist")
    void agencyType() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentAgencyType row = new StudentAgencyType();
            row.setStudentAgencyTypeName(marker());
            Integer id = agencyTypes.create(row).getId();
            withCleanup(id, agencyTypes::delete, () -> {
                StudentAgencyType edit = new StudentAgencyType();
                edit.setStudentAgencyTypeName("AR " + id);
                edit.setStudentAgencyTypeNameEn("EN " + id);
                edit.setStudentAgencyTypeOrder(77);
                edit.setIsActive(false);
                agencyTypes.update(id, edit);

                StudentAgencyType back = agencyTypes.getById(id);
                assertThat(back.getStudentAgencyTypeNameEn()).isEqualTo("EN " + id);
                assertThat(back.getStudentAgencyTypeOrder()).isEqualTo(77);
                assertThat(back.getIsActive()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("MOHE sub: English name, order and Active flag persist, the main category is kept")
    void moheSubMain() {
        Integer main = jdbc.queryForObject("SELECT MIN(MOHE_MAIN_ID) FROM MOHECORE.MOHE_MAIN", Integer.class);
        AsUser.run(AsUser.SUPERADMIN, () -> {
            MoheSubMain row = new MoheSubMain();
            row.setMoheSubMainName(marker());
            row.setMoheMainId(main);
            Integer id = subMains.create(row).getId();
            withCleanup(id, subMains::delete, () -> {
                MoheSubMain edit = new MoheSubMain();
                edit.setMoheSubMainName("AR " + id);
                edit.setMoheSubMainNameEn("EN " + id);
                edit.setMoheSubMainOrder(77);
                edit.setIsActive(false);
                subMains.update(id, edit);

                MoheSubMain back = subMains.getById(id);
                assertThat(back.getMoheSubMainNameEn()).isEqualTo("EN " + id);
                assertThat(back.getMoheSubMainOrder()).isEqualTo(77);
                assertThat(back.getIsActive()).isFalse();
                assertThat(back.getMoheMainId()).as("not sent, so not cleared").isEqualTo(main);
            });
        });
    }
}
