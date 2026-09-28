package com.dilaw.mohe.studentportal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How change-request values are read for their student field (review R2b). */
class ChangeRequestValuesTest {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    @Test
    @DisplayName("the value from the review, 1-1-2000, is 1 January 2000")
    void reviewValueIsADate() {
        assertThat(ChangeRequestValues.normalize(Instant.class, "DATE", "1-1-2000", RIYADH))
                .isEqualTo("2000-01-01");
    }

    @Test
    @DisplayName("year-first, day-first, Arabic-Indic digits and old stored instants are all read")
    void lenientFormats() {
        assertThat(ChangeRequestValues.parseDate("2000-01-31", RIYADH)).isEqualTo(LocalDate.of(2000, 1, 31));
        assertThat(ChangeRequestValues.parseDate("2000/1/31", RIYADH)).isEqualTo(LocalDate.of(2000, 1, 31));
        assertThat(ChangeRequestValues.parseDate("31/01/2000", RIYADH)).isEqualTo(LocalDate.of(2000, 1, 31));
        assertThat(ChangeRequestValues.parseDate("31.1.2000", RIYADH)).isEqualTo(LocalDate.of(2000, 1, 31));
        assertThat(ChangeRequestValues.parseDate("٣١/٠١/٢٠٠٠", RIYADH)).isEqualTo(LocalDate.of(2000, 1, 31));
        // What getCurrentFieldValue stored before: midnight in Riyadh, written in UTC.
        assertThat(ChangeRequestValues.parseDate("2000-05-26T21:00:00Z", RIYADH)).isEqualTo(LocalDate.of(2000, 5, 27));
    }

    @Test
    @DisplayName("impossible dates, typing slips and text are refused")
    void badDatesRefused() {
        assertThat(ChangeRequestValues.parseDate("31-02-2000", RIYADH)).isNull();
        assertThat(ChangeRequestValues.parseDate("1-1-200", RIYADH)).isNull();
        assertThat(ChangeRequestValues.parseDate("1-1-0200", RIYADH)).isNull();
        assertThat(ChangeRequestValues.parseDate("yesterday", RIYADH)).isNull();
        assertThatThrownBy(() -> ChangeRequestValues.normalize(Instant.class, "DATE", "13/13/2000", RIYADH))
                .isInstanceOf(ChangeRequestValues.InvalidValue.class)
                .extracting(e -> ((ChangeRequestValues.InvalidValue) e).kind()).isEqualTo(ChangeRequestValues.DATE);
    }

    @Test
    @DisplayName("a date is stored as the first instant of that day in the application zone")
    void dateAppliedAtStartOfDay() {
        assertThat(ChangeRequestValues.toFieldValue(Instant.class, "2000-01-01", RIYADH))
                .isEqualTo(Instant.parse("1999-12-31T21:00:00Z"));
        assertThat(ChangeRequestValues.display(Instant.parse("1999-12-31T21:00:00Z"), RIYADH))
                .isEqualTo("2000-01-01");
    }

    @Test
    @DisplayName("a lookup id must be a number; an email field must look like one; text passes")
    void otherTypes() {
        assertThat(ChangeRequestValues.normalize(Integer.class, "LOOKUP", " ١٢ ", RIYADH)).isEqualTo("12");
        assertThatThrownBy(() -> ChangeRequestValues.normalize(Integer.class, "LOOKUP", "Yemen", RIYADH))
                .isInstanceOf(ChangeRequestValues.InvalidValue.class);
        assertThatThrownBy(() -> ChangeRequestValues.normalize(String.class, "EMAIL", "not-an-email", RIYADH))
                .isInstanceOf(ChangeRequestValues.InvalidValue.class);
        assertThat(ChangeRequestValues.normalize(String.class, "EMAIL", " a@b.co ", RIYADH)).isEqualTo("a@b.co");
        assertThat(ChangeRequestValues.normalize(String.class, "TEXT", " أحمد ", RIYADH)).isEqualTo("أحمد");
    }
}
