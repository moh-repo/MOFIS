package com.dilaw.mohe.codelist;

import com.dilaw.mohe.model.LuQuarterNumber;
import com.dilaw.mohe.service.LuQuarterNumberService;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one reader of the code lists: cached, evicted by every write the
 * application makes, protective of the codes Java depends on.
 */
@Tag("integration")
@SpringBootTest
class CodeListServiceTest {

    @Autowired private CodeListService codeLists;
    @Autowired private LuQuarterNumberService quarterNumberService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a read is served from the cache until the code-list API writes")
    void writesThroughTheApiEvict() {
        CodeRow low = codeLists.require("alert-severity", "LOW");
        String originalEn = low.nameEn();
        try {
            // A change made behind the cache is not seen…
            jdbc.update("UPDATE MOHECORE.LU_ALERT_SEVERITY SET NAME_EN = 'Low (changed)' WHERE CODE = 'LOW'");
            assertThat(codeLists.require("alert-severity", "LOW").nameEn()).isEqualTo(originalEn);
            // …until a write through the service evicts it.
            codeLists.update("alert-severity", low.id(), new CodeListService.CodeRowInput(
                    null, null, "Low (changed twice)", null, null, null, null, null));
            assertThat(codeLists.require("alert-severity", "LOW").nameEn()).isEqualTo("Low (changed twice)");
        } finally {
            jdbc.update("UPDATE MOHECORE.LU_ALERT_SEVERITY SET NAME_EN = ? WHERE CODE = 'LOW'", originalEn);
            codeLists.evictAll();
        }
    }

    @Test
    @DisplayName("the lookup screen's own services evict the same cache")
    void lookupScreenWritesEvict() {
        LuQuarterNumber q3 = quarterNumberService.getAll().stream()
                .filter(q -> "3".equals(q.getCode())).findFirst().orElseThrow();
        String original = q3.getNameEn();
        codeLists.rows("quarter-number"); // warm
        try {
            q3.setNameEn("Quarter 3 (screen)");
            quarterNumberService.update(q3.getId(), q3);
            assertThat(codeLists.label("quarter-number", "3", "en")).isEqualTo("Quarter 3 (screen)");
        } finally {
            q3.setNameEn(original);
            quarterNumberService.update(q3.getId(), q3);
            codeLists.evictAll();
        }
    }

    @Test
    @DisplayName("a system code cannot be renamed, deactivated or deleted through the API")
    void systemRowsAreProtected() {
        CodeRow critical = codeLists.require("alert-severity", "CRITICAL");
        assertThat(critical.system()).isTrue();

        assertThatThrownBy(() -> codeLists.delete("alert-severity", critical.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> codeLists.update("alert-severity", critical.id(),
                new CodeListService.CodeRowInput("SEVERE", null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> codeLists.update("alert-severity", critical.id(),
                new CodeListService.CodeRowInput(null, null, null, null, null, null, false, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(codeLists.require("alert-severity", "CRITICAL").active()).isTrue();
    }

    @Test
    @DisplayName("an adapter list (another table's shape) is read-only here")
    void adaptersAreReadOnly() {
        assertThatThrownBy(() -> codeLists.create("study-status",
                new CodeListService.CodeRowInput("X", "س", "X", 1, null, null, true, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(codeLists.rows("study-status")).anySatisfy(r -> assertThat(r.code()).isEqualTo("ACTIVE"));
        assertThat(codeLists.find("study-status", "ACTIVE").orElseThrow().uiColor()).matches("#[0-9A-Fa-f]{6}");
    }

    @Test
    @DisplayName("labels fall back to the other language, then to the code; severities outside PrimeNG's vocabulary read as secondary")
    void labelsAndSeverities() {
        assertThat(codeLists.label("alert-severity", "HIGH", "en")).isEqualTo("High");
        assertThat(codeLists.label("alert-severity", "HIGH", "ar")).isNotBlank();
        assertThat(codeLists.label("alert-severity", "NOPE", "en")).isEqualTo("NOPE");
        assertThat(codeLists.severity("alert-severity", "CRITICAL")).isEqualTo("danger");
        assertThat(codeLists.severity("alert-severity", "NOPE")).isEqualTo("secondary");
        assertThat(codeLists.all()).containsKeys("alert-severity", "approval-status", "report-label", "study-status");
    }
}
