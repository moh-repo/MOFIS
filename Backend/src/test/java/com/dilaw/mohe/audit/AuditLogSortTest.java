package com.dilaw.mohe.audit;

import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.util.SortWhitelist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order the audit-log screen asks for has to be an order the database can
 * actually produce.
 *
 * <p>The screen shows one page of a table with more than eleven thousand rows,
 * so its column headers cannot sort in the browser: each click asks the server
 * for the same page ordered differently. That makes every entry of
 * {@link MoheAuditLogController#SORTABLE} a promise about SQL, and a property
 * that does not resolve against the entity is a 500 the user meets by clicking
 * a header — which is why these run against the database rather than asserting
 * on strings.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class AuditLogSortTest {

    @Autowired
    private MoheAuditLogService auditLogService;

    static Stream<String> sortableColumns() {
        return MoheAuditLogController.SORTABLE.stream();
    }

    @ParameterizedTest(name = "sortBy={0}")
    @MethodSource("sortableColumns")
    @DisplayName("every column a header offers produces runnable SQL, in both directions")
    void everyColumn_executes(String column) {
        assertThatCode(() -> AsUser.call(AsUser.SUPERADMIN, () -> {
            auditLogService.getAllAuditLogs(0, 5, Sort.by(Sort.Direction.ASC, column)).getContent();
            auditLogService.getAllAuditLogs(0, 5, Sort.by(Sort.Direction.DESC, column)).getContent();
            // The filtered branch is a different query and takes the same sort.
            return auditLogService.getAuditLogsWithFilters("STUDENT_DATA", null, null, null,
                    null, null, 0, 5, Sort.by(Sort.Direction.ASC, column)).getContent();
        })).as("sortBy=%s must resolve against MoheAuditLog", column).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the rows really come back in the order that was asked for")
    void theOrderIsApplied() {
        List<MoheAuditLog> ascending = AsUser.call(AsUser.SUPERADMIN,
                () -> auditLogService.getAllAuditLogs(0, 50, Sort.by(Sort.Direction.ASC, "logId")).getContent());
        assertThat(ascending).isSortedAccordingTo((a, b) -> Long.compare(a.getLogId(), b.getLogId()));

        List<MoheAuditLog> descending = AsUser.call(AsUser.SUPERADMIN,
                () -> auditLogService.getAllAuditLogs(0, 50, Sort.by(Sort.Direction.DESC, "logId")).getContent());
        assertThat(descending).isSortedAccordingTo((a, b) -> Long.compare(b.getLogId(), a.getLogId()));
    }

    @Test
    @DisplayName("no sort at all is still newest first")
    void defaultIsNewestFirst() {
        List<MoheAuditLog> page = AsUser.call(AsUser.SUPERADMIN,
                () -> auditLogService.getAllAuditLogs(0, 20).getContent());
        assertThat(page).isSortedAccordingTo((a, b) -> b.getChangedAt().compareTo(a.getChangedAt()));
    }

    @Test
    @DisplayName("a column the screen does not show is refused, not guessed at")
    void unlistedColumnIsRefused() {
        // oldValues is a real property and a real column; it is simply not one the
        // screen offers, and a caller naming it is a caller reaching past the screen.
        assertThatThrownBy(() -> SortWhitelist.sort("oldValues", "asc", MoheAuditLogController.SORTABLE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot sort by 'oldValues'")
                .hasMessageContaining("changedAt");
    }

    @Test
    @DisplayName("a blank sort falls back to the first entry, which is the date")
    void blankFallsBackToTheDate() {
        assertThat(SortWhitelist.sort(null, "desc", MoheAuditLogController.SORTABLE).toString())
                .isEqualTo("changedAt: DESC");
    }
}
