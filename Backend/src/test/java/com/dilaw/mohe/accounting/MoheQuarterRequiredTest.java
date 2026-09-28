package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.exception.LocalizedStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Round-5 QA R5-20: an allocation for a quarter with no MOHE_QUARTER row reached the
 * foreign key and was answered "This record is still used by other records, so it cannot
 * be removed". The quarter is now checked first, with a message that says which one.
 */
class MoheQuarterRequiredTest {

    private FiscalPeriodService serviceWithRows(int rows) {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        Mockito.when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(20259))).thenReturn(0);
        Mockito.when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(20253))).thenReturn(rows);
        FiscalPeriodService service = new FiscalPeriodService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        return service;
    }

    @Test
    @DisplayName("a quarter with no MOHE_QUARTER row is a 400 naming the year and quarter")
    void missingQuarterRefused() {
        assertThatThrownBy(() -> serviceWithRows(1).requireMoheQuarter(20259))
                .isInstanceOf(LocalizedStatusException.class)
                .satisfies(e -> {
                    LocalizedStatusException refusal = (LocalizedStatusException) e;
                    assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(refusal.messageKey()).isEqualTo("accounting.quarter.missing");
                    assertThat(refusal.messageArgs()).containsExactly("2025", "9");
                });
        assertThatThrownBy(() -> serviceWithRows(1).requireMoheQuarter(null))
                .isInstanceOf(LocalizedStatusException.class);
    }

    @Test
    @DisplayName("a quarter on the list passes")
    void existingQuarterPasses() {
        assertThatCode(() -> serviceWithRows(1).requireMoheQuarter(20253)).doesNotThrowAnyException();
    }
}
