package com.dilaw.mohe.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link SortWhitelist}: only listed properties, and a 400 that names the rest (plan item 2.4). */
class SortWhitelistTest {

    private static final Set<String> ALLOWED = new LinkedHashSet<>(List.of("createdAt", "studentId", "status"));

    @Test
    @DisplayName("a listed property sorts in the requested direction")
    void listedProperty() {
        assertThat(SortWhitelist.sort("studentId", "asc", ALLOWED).getOrderFor("studentId").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(SortWhitelist.sort("studentId", "DESC", ALLOWED).getOrderFor("studentId").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("anything but asc is descending, as every caller already assumed")
    void directionDefaultsToDescending() {
        assertThat(SortWhitelist.order("status", null, ALLOWED).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(SortWhitelist.order("status", "sideways", ALLOWED).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(SortWhitelist.order("status", " Asc ", ALLOWED).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("no property means the first listed one")
    void blankFallsBackToFirst() {
        assertThat(SortWhitelist.order(null, "asc", ALLOWED).getProperty()).isEqualTo("createdAt");
        assertThat(SortWhitelist.order("  ", "asc", ALLOWED).getProperty()).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("an unlisted property is a 400 that lists what is sortable — never a 500 from the query")
    void unknownPropertyIs400() {
        assertThatThrownBy(() -> SortWhitelist.sort("passwordHash", "asc", ALLOWED))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("passwordHash").contains("createdAt, status, studentId");
                });
    }

    @Test
    @DisplayName("a JPA path traversal is just another unlisted property")
    void pathsAreNotSpecial() {
        assertThatThrownBy(() -> SortWhitelist.sort("student.passwordHash", "asc", ALLOWED))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("an empty allow-list is a programming error, not a request error")
    void emptyAllowListIsABug() {
        assertThatThrownBy(() -> SortWhitelist.sort("x", "asc", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
