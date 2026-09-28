package com.dilaw.mohe.service;

import com.dilaw.mohe.service.StudentReferenceValidator.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA M18 (25 Sep 2026): a portal change request naming university 99999999 was
 * accepted and failed only when approved, on the foreign key. One field's value is now
 * checked against its lookup table when it is submitted; a row no longer in use is
 * refused as well. The SQL itself is checked against the database in
 * {@code StudentReferenceValidatorTest} (integration).
 */
class StudentReferenceCheckTest {

    private JdbcTemplate jdbc;
    private StudentReferenceValidator validator;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        validator = new StudentReferenceValidator();
        ReflectionTestUtils.setField(validator, "jdbcTemplate", jdbc);
    }

    @Test
    @DisplayName("P20: a university id with no row is MISSING")
    void missing() {
        when(jdbc.queryForList(contains("MOHECORE.STUDY_UNIVERSITY_IN"), eq(Boolean.class), eq(99_999_999)))
                .thenReturn(List.of());
        assertThat(validator.check("studyUniversityIn", "99999999")).isEqualTo(Reference.MISSING);
    }

    @Test
    @DisplayName("a row that exists: EXISTS when active, INACTIVE when retired")
    void existsOrInactive() {
        when(jdbc.queryForList(contains("MOHECORE.STUDENT_GENDER"), eq(Boolean.class), eq(1))).thenReturn(List.of(true));
        when(jdbc.queryForList(contains("MOHECORE.STUDENT_GENDER"), eq(Boolean.class), eq(3))).thenReturn(List.of(false));
        assertThat(validator.check("studentGender", "1")).isEqualTo(Reference.EXISTS);
        assertThat(validator.check("studentGender", "3")).isEqualTo(Reference.INACTIVE);
    }

    @Test
    @DisplayName("text where an id belongs is MISSING; a field no key backs is not looked up")
    void notAnIdOrNotALookup() {
        assertThat(validator.check("studyCountryIn", "Yemen")).isEqualTo(Reference.MISSING);
        assertThat(validator.check("studentName", "Ali")).isEqualTo(Reference.NOT_A_LOOKUP);
        verify(jdbc, never()).queryForList(contains("STUDENT_NAME"), eq(Boolean.class), Mockito.any());
    }
}
