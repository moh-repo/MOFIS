package com.dilaw.mohe.ums.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Account e-mail and phone are checked when new or changed (review M8). */
class AccountContactShapeTest {

    @Test
    @DisplayName("a malformed e-mail or a short phone is refused")
    void refused() {
        assertThatThrownBy(() -> UmsUserService.requireContactShape("not-an-email", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UmsUserService.requireContactShape(null, null, "12-34", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("valid values, blanks and unchanged old values pass")
    void accepted() {
        assertThatCode(() -> UmsUserService.requireContactShape("a@mohe.gov.ye", null, "+967 777 123 456", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> UmsUserService.requireContactShape("", null, " ", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> UmsUserService.requireContactShape("legacy", "legacy", "123", "123"))
                .as("an old malformed value nobody touched does not block an unrelated edit")
                .doesNotThrowAnyException();
    }
}
