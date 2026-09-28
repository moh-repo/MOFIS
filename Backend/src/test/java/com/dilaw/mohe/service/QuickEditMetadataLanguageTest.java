package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentQuickEditMetadataDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick Edit's option lists in the reader's language, without switched-off rows
 * (QA B5, UI-001): an English screen offered Arabic genders and statuses, and the
 * gender list included an inactive "test".
 */
@Tag("integration")
@SpringBootTest
class QuickEditMetadataLanguageTest {

    @Autowired private StudentQuickEditMetadataService metadata;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private List<String> genders(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        StudentQuickEditMetadataDto dto = metadata.getMetadata();
        return dto.getGenders().stream().map(LookupOptionDto::getName).toList();
    }

    @Test
    @DisplayName("English names in English, Arabic in Arabic")
    void namesFollowTheLanguage() {
        List<String> en = jdbc.queryForList(
                "SELECT STUDENT_GENDER_NAME_EN FROM MOHECORE.STUDENT_GENDER "
                        + "WHERE ISNULL(IS_ACTIVE, 1) = 1 AND STUDENT_GENDER_NAME_EN IS NOT NULL AND STUDENT_GENDER_NAME_EN <> ''",
                String.class);

        assertThat(genders(Locale.ENGLISH)).containsAll(en);
        assertThat(genders(Locale.forLanguageTag("ar"))).doesNotContainAnyElementsOf(en);
    }

    @Test
    @DisplayName("a switched-off row is not offered")
    void inactiveRowsAreLeftOut() {
        List<String> inactive = jdbc.queryForList(
                "SELECT STUDENT_GENDER_NAME FROM MOHECORE.STUDENT_GENDER WHERE IS_ACTIVE = 0", String.class);

        assertThat(genders(Locale.forLanguageTag("ar"))).doesNotContainAnyElementsOf(inactive);
    }
}
