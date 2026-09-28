package com.dilaw.mohe.util;

import com.dilaw.mohe.exception.ErrorDetails;
import com.dilaw.mohe.exception.GlobalExceptionHandler;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudentDegree;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Live test M12 (23 Sep 2026): a second "جامعية" degree was accepted with a 201.
 * The count query is mocked at the Criteria API, so what is checked here is the
 * shape of the question asked — trimmed, lower-cased, the edited row left out, the
 * parent honoured — and the answer given when the count is not zero.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LookupNameUniquenessTest {

    @Mock private EntityManager em;
    @Mock private CriteriaBuilder cb;
    @Mock(answer = Answers.RETURNS_SELF) private CriteriaQuery<Long> query;
    @Mock private Root<StudentDegree> root;
    @Mock private Path<String> namePath;
    @Mock private Path<Object> idPath;
    @Mock private Path<Object> parentPath;
    @Mock private Expression<String> trimmed;
    @Mock private Expression<String> lowered;
    @Mock private Expression<Long> count;
    @Mock private Predicate sameName;
    @Mock private Predicate otherRow;
    @Mock private Predicate sameParent;
    @Mock private Predicate noParent;
    @Mock private Predicate both;
    @Mock private TypedQuery<Long> typed;

    @BeforeEach
    void wireCriteria() {
        when(em.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Long.class)).thenReturn(query);
        when(query.from(StudentDegree.class)).thenReturn(root);
        when(root.<String>get("studentDegreeName")).thenReturn(namePath);
        when(root.get("id")).thenReturn(idPath);
        when(root.get("studyUniversityInId")).thenReturn(parentPath);
        when(cb.trim(namePath)).thenReturn(trimmed);
        when(cb.lower(trimmed)).thenReturn(lowered);
        when(cb.equal(eq(lowered), anyString())).thenReturn(sameName);
        when(cb.notEqual(idPath, 7)).thenReturn(otherRow);
        when(cb.equal(parentPath, 3)).thenReturn(sameParent);
        when(cb.isNull(parentPath)).thenReturn(noParent);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(both);
        when(cb.count(root)).thenReturn(count);
        when(em.createQuery(query)).thenReturn(typed);
    }

    private void require(String name, Integer excludeId) {
        LookupNameUniqueness.require(em, StudentDegree.class, "studentDegreeName", name, "id", excludeId);
    }

    private static WebRequest request(String acceptLanguage) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mohe/api/v1/lookup/student-degree");
        req.addHeader("Accept-Language", acceptLanguage);
        return new ServletWebRequest(req);
    }

    @Test
    @DisplayName("a blank name asks the database nothing")
    void blankNameIsNotChecked() {
        assertThatCode(() -> require("   ", null)).doesNotThrowAnyException();
        assertThatCode(() -> require(null, 7)).doesNotThrowAnyException();

        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("no other row with the name: the save goes ahead, asked with the name trimmed and lower-cased")
    void countZeroPasses() {
        when(typed.getSingleResult()).thenReturn(0L);

        assertThatCode(() -> require("  Master's ", null)).doesNotThrowAnyException();

        verify(cb).equal(lowered, "master's");
        verify(cb, never()).notEqual(any(), any(Object.class));
        verify(query).where(sameName);
    }

    @Test
    @DisplayName("another row already has the name: a 409 that names it, in the reader's language")
    void countOneIsRefused() {
        when(typed.getSingleResult()).thenReturn(1L);

        LocalizedStatusException ex = catchThrowableOfType(LocalizedStatusException.class,
                () -> require("جامعية", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).isEqualTo("A row named \"جامعية\" already exists in this list.");
        assertThat(ex.messageKey()).isEqualTo("lookup.duplicateName");
        assertThat(ex.messageArgs()).containsExactly("جامعية");
    }

    @Test
    @DisplayName("the refusal reaches the client as 409 with the bundle's sentence in both languages")
    void answeredInBothLanguages() {
        when(typed.getSingleResult()).thenReturn(2L);
        LocalizedStatusException ex = catchThrowableOfType(LocalizedStatusException.class,
                () -> require("جامعية", null));

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(handler, "messageSource", messages);

        ResponseEntity<?> en = handler.responseStatusException(ex, request("en"));
        ResponseEntity<?> ar = handler.responseStatusException(ex, request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ErrorDetails) en.getBody()).getMessage())
                .isEqualTo("A row named \"جامعية\" already exists in this list.");
        assertThat(ar.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ErrorDetails) ar.getBody()).getMessage())
                .isEqualTo("يوجد صف بالاسم \"جامعية\" في هذه القائمة بالفعل.");
    }

    @Test
    @DisplayName("an edit leaves its own row out of the count")
    void editExcludesItsOwnRow() {
        when(typed.getSingleResult()).thenReturn(0L);

        assertThatCode(() -> require("جامعية", 7)).doesNotThrowAnyException();

        verify(cb).notEqual(idPath, 7);
        verify(cb).and(sameName, otherRow);
        verify(query).where(both);
    }

    @Test
    @DisplayName("a parented list counts only the rows under the same parent")
    void parentScopesTheCount() {
        when(typed.getSingleResult()).thenReturn(0L);

        assertThatCode(() -> LookupNameUniqueness.require(em, StudentDegree.class, "studentDegreeName", "كلية الطب",
                "id", null, "studyUniversityInId", 3)).doesNotThrowAnyException();
        verify(cb).equal(parentPath, 3);
        verify(cb).and(sameName, sameParent);

        assertThatCode(() -> LookupNameUniqueness.require(em, StudentDegree.class, "studentDegreeName", "كلية الطب",
                "id", null, "studyUniversityInId", null)).doesNotThrowAnyException();
        verify(cb).isNull(parentPath);
        verify(cb).and(sameName, noParent);
    }

    @Test
    @DisplayName("a row keeping its own name is unchanged; moving it to another parent is not")
    void unchangedMeansSameNameAndParent() {
        assertThat(LookupNameUniqueness.same("جامعية", " جامعية ")).isTrue();
        assertThat(LookupNameUniqueness.same("Master", "master")).isTrue();
        assertThat(LookupNameUniqueness.same("جامعية", "ماجستير")).isFalse();
        assertThat(LookupNameUniqueness.same("جامعية", null)).as("a body that omits the name claims nothing").isFalse();
        assertThat(LookupNameUniqueness.same(null, null)).isFalse();

        assertThat(LookupNameUniqueness.unchanged("الفرع الرئيسي", "الفرع الرئيسي", 4, 4)).isTrue();
        assertThat(LookupNameUniqueness.unchanged("الفرع الرئيسي", "الفرع الرئيسي", 4, 5)).isFalse();
        assertThat(LookupNameUniqueness.unchanged("الفرع الرئيسي", "فرع", 4, 4)).isFalse();
    }
}
