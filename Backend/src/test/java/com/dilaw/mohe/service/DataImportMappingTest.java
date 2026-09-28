package com.dilaw.mohe.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The generic import's column mapping, which used to be a no-op.
 *
 * <p>{@code sourceColumns.indexOf(sourceColumns.get(j))} always answered {@code j} — or,
 * with two source headers spelt the same, the first one's index — so the mapping the
 * caller sent was never consulted. The contract is now explicit and checked.
 */
class DataImportMappingTest {

    @Test
    @DisplayName("cells are taken by position, one per target column")
    void valuesFollowPosition() {
        Object[] values = DataImportService.rowValues(List.of("a", "b", "c"), 3);
        assertThat(values).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("a short row is null-padded rather than failing")
    void shortRowIsPadded() {
        Object[] values = DataImportService.rowValues(List.of("a"), 3);
        assertThat(values).containsExactly("a", null, null);
    }

    @Test
    @DisplayName("a long row is truncated to the mapped columns")
    void longRowIsTruncated() {
        Object[] values = DataImportService.rowValues(List.of("a", "b", "c", "d"), 2);
        assertThat(values).containsExactly("a", "b");
    }

    @Test
    @DisplayName("source and target lists must pair up one-to-one")
    void mismatchedListsAreRejected() {
        assertThatThrownBy(() -> DataImportService.requirePairwiseMapping(List.of("a", "b"), List.of("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one-to-one");
        assertThatThrownBy(() -> DataImportService.requirePairwiseMapping(null, List.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("equal-length lists pass, duplicate source headers included")
    void pairedListsPass() {
        DataImportService.requirePairwiseMapping(List.of("NAME", "NAME"), List.of("FIRST", "LAST"));
    }
}
