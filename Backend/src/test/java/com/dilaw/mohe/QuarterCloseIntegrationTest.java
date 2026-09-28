package com.dilaw.mohe;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.service.MoheQuarterCloseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the quarter close flow.
 * Requires SQL Server. Tests are idempotent — they do not close real quarters.
 */
@SpringBootTest
@Tag("integration")
class QuarterCloseIntegrationTest {

    @Autowired
    private MoheQuarterCloseService closeService;

    @Autowired
    private MoheQuarterRepository quarterRepository;

    @Test
    @DisplayName("Closing non-existent quarter ID returns 404")
    void closeNonExistentQuarter_returns404() {
        assertThatThrownBy(() -> closeService.closeQuarter(Integer.MAX_VALUE, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("getCurrent returns open quarter or empty when all are closed")
    void getCurrent_returnsOpenOrEmpty() {
        Optional<MoheQuarter> current = quarterRepository
                .findTopByQuarterClosedFalseOrderByQuarterYearDescQuarterNumberDesc();
        // Either returns an open quarter or empty — both are valid
        current.ifPresent(q -> {
            assertThat(q.getQuarterClosed()).isFalse();
            assertThat(q.getQuarterYear()).isNotNull();
        });
    }
}
