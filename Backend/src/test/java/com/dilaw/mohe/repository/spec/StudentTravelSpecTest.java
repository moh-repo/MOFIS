package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentTravelTicket;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two filters behind the Travel Tickets page's list (24 Sep 2026): each
 * narrows on its own column when given, and steps aside when not, so the
 * country scope they are combined with is all that remains.
 */
@SuppressWarnings("unchecked")
class StudentTravelSpecTest {

    private Root<StudentTravelTicket> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Predicate everything;

    @BeforeEach
    void mocks() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        everything = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(everything);
    }

    @Test
    @DisplayName("a student narrows on STUDENT_ID")
    void byStudentNarrows() {
        Path<Object> column = mock(Path.class);
        Predicate match = mock(Predicate.class);
        when(root.get("studentId")).thenReturn(column);
        when(cb.equal(column, 1003)).thenReturn(match);

        assertThat(StudentTravelSpec.byStudent(1003).toPredicate(root, query, cb)).isSameAs(match);
    }

    @Test
    @DisplayName("no student means no narrowing")
    void byStudentStepsAside() {
        assertThat(StudentTravelSpec.byStudent(null).toPredicate(root, query, cb)).isSameAs(everything);
        verify(root, never()).get(anyString());
    }

    @Test
    @DisplayName("a travel type narrows on TRAVEL_TYPE, trimmed")
    void byTypeNarrows() {
        Path<Object> column = mock(Path.class);
        Predicate match = mock(Predicate.class);
        when(root.get("travelType")).thenReturn(column);
        when(cb.equal(column, "WITH_FAMILY")).thenReturn(match);

        assertThat(StudentTravelSpec.byType(" WITH_FAMILY ").toPredicate(root, query, cb)).isSameAs(match);
    }

    @Test
    @DisplayName("no or blank travel type means no narrowing")
    void byTypeStepsAside() {
        assertThat(StudentTravelSpec.byType(null).toPredicate(root, query, cb)).isSameAs(everything);
        assertThat(StudentTravelSpec.byType("   ").toPredicate(root, query, cb)).isSameAs(everything);
        verify(root, never()).get(anyString());
    }
}
