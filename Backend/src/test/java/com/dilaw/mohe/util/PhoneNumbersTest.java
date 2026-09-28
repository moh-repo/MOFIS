package com.dilaw.mohe.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link PhoneNumbers}: digit count, not character count (review item S1). */
class PhoneNumbersTest {

    @Test
    @DisplayName("blank is acceptable: the contact fields are optional")
    void blankIsValid() {
        assertThat(PhoneNumbers.isValid(null, true)).isTrue();
        assertThat(PhoneNumbers.isValid("   ", false)).isTrue();
    }

    @Test
    @DisplayName("a mobile needs at least 9 digits, a landline at least 8")
    void minimums() {
        assertThat(PhoneNumbers.isValid("77712345", true)).isFalse();
        assertThat(PhoneNumbers.isValid("777123456", true)).isTrue();
        assertThat(PhoneNumbers.isValid("01234567", false)).isTrue();
        assertThat(PhoneNumbers.isValid("0123456", false)).isFalse();
    }

    @Test
    @DisplayName("separators do not count towards the length")
    void separatorsAreNotDigits() {
        assertThat(PhoneNumbers.isValid("12-34", false)).isFalse();
        assertThat(PhoneNumbers.isValid("(((1)))", false)).isFalse();
        assertThat(PhoneNumbers.isValid("+967 777-123-456", true)).isTrue();
        assertThat(PhoneNumbers.isValid("(01) 234.567", false)).isTrue();
    }

    @Test
    @DisplayName("at most 15 digits, and no letters or a plus sign in the middle")
    void maximumAndShape() {
        assertThat(PhoneNumbers.isValid("123456789012345", false)).isTrue();
        assertThat(PhoneNumbers.isValid("1234567890123456", false)).isFalse();
        assertThat(PhoneNumbers.isValid("777 123 45a", true)).isFalse();
        assertThat(PhoneNumbers.isValid("777+123456", true)).isFalse();
    }

    @Test
    @DisplayName("Arabic-Indic digits are counted like Latin ones")
    void arabicIndicDigits() {
        assertThat(PhoneNumbers.isValid("٧٧٧١٢٣٤٥٦", true)).isTrue();
        assertThat(PhoneNumbers.isValid("٧٧٧١٢", true)).isFalse();
    }

    @Test
    @DisplayName("require names the field; requireIfChanged leaves an untouched old number alone")
    void requireAndRequireIfChanged() {
        assertThatThrownBy(() -> PhoneNumbers.require("studentCellphone", "1234", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("studentCellphone");

        assertThatCode(() -> PhoneNumbers.requireIfChanged("studentHomeNumber", "1234", " 1234 ", false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PhoneNumbers.requireIfChanged("studentHomeNumber", "1234", "12345", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> PhoneNumbers.requireIfChanged("studentHomeNumber", "1234", "", false))
                .doesNotThrowAnyException();
    }
}
