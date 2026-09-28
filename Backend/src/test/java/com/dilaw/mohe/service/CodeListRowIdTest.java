package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.EnumOptionDto;
import com.dilaw.mohe.service.codelist.CodeRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A code-list row reaches the browser with its key.
 *
 * <p>The Code Lists screen edits, toggles and deletes a row by {@code id}, and the
 * DTO carried everything but that: the Active toggle sent no request, Delete did
 * nothing and Edit was posted as a create that failed with 409 (live QA #3).
 */
class CodeListRowIdTest {

    @Test
    @DisplayName("the row's id travels with it")
    void idIsSent() {
        CodeRow row = new CodeRow(42, "QAT", "اختبار", "Test", 3, "info", null, false, true, null);

        EnumOptionDto dto = EnumOptionsService.toDto(row, "ar");

        assertThat(dto.getId()).isEqualTo(42);
        assertThat(dto.getValue()).isEqualTo("QAT");
        assertThat(dto.getActive()).isTrue();
    }
}
