package com.dilaw.mohe.service;

import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link StudentReferenceValidator#requireExistingReferences}: a student is checked
 * for lookup ids that point at no row when it is submitted, not when it is approved
 * (review A2; plan W4-10).
 *
 * <p>A student edit is stored as an approval request and reaches STUDENT_DATA only on
 * approval, so the foreign keys were first enforced by the approver's click — the
 * submitter saw success and the approver an FK error they could do nothing about.
 * What is pinned: a student as it is on file passes; every broken field is named, and
 * only those, as {@code field.<name>} resolvables under {@code student.reference.missing}
 * so the answer is in the reader's language; null and 0 ("not set") are never
 * looked up; and the list agrees with the foreign keys STUDENT_DATA actually has, so a
 * key added later without a matching check fails here rather than at an approval.
 *
 * <p>Against the real database, because the checks are SQL built from table and
 * column names: a misspelt one is an SQL error, which a mocked JdbcTemplate would
 * never show. Nothing is written — STUDENT_DATA is only read, into a detached copy.
 */
@Tag("integration")
@SpringBootTest
class StudentReferenceValidatorTest {

    /** An id no lookup table will ever reach. */
    private static final int NO_SUCH_ROW = 987_654_321;

    @Autowired private StudentReferenceValidator validator;
    @Autowired private StudentDataRepository studentRepository;
    @Autowired private JdbcTemplate jdbc;

    /** The Java field behind each STUDENT_DATA column. */
    private static final Map<String, Field> FIELD_BY_COLUMN = Arrays.stream(StudentDatum.class.getDeclaredFields())
            .filter(f -> f.isAnnotationPresent(Column.class))
            .collect(Collectors.toMap(f -> f.getAnnotation(Column.class).name().toUpperCase(Locale.ROOT), f -> f,
                    (first, second) -> first));

    /** The columns of STUDENT_DATA that carry a foreign key, read from the catalogue. */
    private List<String> foreignKeyColumns() {
        return jdbc.queryForList("""
                SELECT DISTINCT UPPER(c.name)
                FROM sys.foreign_key_columns fkc
                JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
                WHERE fkc.parent_object_id = OBJECT_ID('MOHECORE.STUDENT_DATA')
                """, String.class);
    }

    /** A detached copy of a student on file: the validator sees real ids, and STUDENT_DATA is never touched. */
    private StudentDatum aStudentOnFile() {
        Integer id = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                Integer.class);
        StudentDatum copy = new StudentDatum();
        BeanUtils.copyProperties(studentRepository.findById(id).orElseThrow(), copy);
        return copy;
    }

    private static void setEveryForeignKey(StudentDatum student, List<String> columns, Integer value) {
        for (String column : columns) {
            ReflectionTestUtils.setField(student, FIELD_BY_COLUMN.get(column).getName(), value);
        }
    }

    /** The field names the refusal lists, from its {@code field.<name>} codes. */
    private static List<String> namedFields(LocalizedArgumentException refused) {
        assertThat(refused.messageKey()).isEqualTo("student.reference.missing");
        assertThat(refused.messageArgs()).hasSize(1);
        assertThat(refused.messageArgs()[0]).isInstanceOf(Collection.class);
        return ((Collection<?>) refused.messageArgs()[0]).stream()
                .map(o -> ((MessageSourceResolvable) o).getCodes()[0])
                .peek(code -> assertThat(code).startsWith("field."))
                .map(code -> code.substring("field.".length()))
                .toList();
    }

    private LocalizedArgumentException refusalOf(StudentDatum student) {
        return catchThrowableOfType(LocalizedArgumentException.class, () -> validator.requireExistingReferences(student));
    }

    @Test
    @DisplayName("a student as it is on file passes: every id it carries has its row")
    void studentOnFilePasses() {
        StudentDatum student = aStudentOnFile();

        assertThatCode(() -> validator.requireExistingReferences(student)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("only the fields that point at no row are named, and the English message lists them too")
    void onlyTheBrokenFieldsAreNamed() {
        StudentDatum student = aStudentOnFile();
        student.setStudyCountryIn(NO_SUCH_ROW);
        student.setStudentGovId(NO_SUCH_ROW);

        LocalizedArgumentException refused = refusalOf(student);

        assertThat(refused).as("refused").isNotNull().isInstanceOf(IllegalArgumentException.class);
        assertThat(namedFields(refused)).containsExactlyInAnyOrder("studyCountryIn", "studentGovId");
        assertThat(refused.getMessage()).as("the log line").contains("studyCountryIn").contains("studentGovId");
    }

    @Test
    @DisplayName("every foreign key STUDENT_DATA has is checked, and each broken one is named")
    void theChecksMirrorTheForeignKeys() {
        List<String> columns = foreignKeyColumns();
        assertThat(columns).as("STUDENT_DATA has its lookup foreign keys").isNotEmpty();
        assertThat(FIELD_BY_COLUMN).as("every key column is mapped on StudentDatum").containsKeys(columns.toArray(String[]::new));

        StudentDatum student = new StudentDatum();
        setEveryForeignKey(student, columns, NO_SUCH_ROW);

        LocalizedArgumentException refused = refusalOf(student);

        assertThat(refused).as("refused").isNotNull();
        assertThat(namedFields(refused))
                .as("a key without a check is found by the approver, which is what A2 closed")
                .containsExactlyInAnyOrderElementsOf(columns.stream().map(c -> FIELD_BY_COLUMN.get(c).getName()).toList());
    }

    @Test
    @DisplayName("the passport-issue country is checked against the table its key names, not the long country list")
    void passportCountryIsCheckedAgainstStudentCountry() {
        // The A2 incident: the wizard offered STUDENT_COUNTRY_ALL, the key is to STUDENT_COUNTRY.
        List<Integer> onlyInTheLongList = jdbc.queryForList("""
                SELECT TOP 1 a.STUDENT_COUNTRY_ALL_ID FROM MOHECORE.STUDENT_COUNTRY_ALL a
                WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_COUNTRY c WHERE c.STUDENT_COUNTRY_ID = a.STUDENT_COUNTRY_ALL_ID)
                ORDER BY a.STUDENT_COUNTRY_ALL_ID
                """, Integer.class);
        org.junit.jupiter.api.Assumptions.assumeFalse(onlyInTheLongList.isEmpty(),
                "every STUDENT_COUNTRY_ALL id also exists in STUDENT_COUNTRY");

        StudentDatum student = aStudentOnFile();
        student.setStudentPassportIssueCountryId(onlyInTheLongList.get(0));

        LocalizedArgumentException refused = refusalOf(student);

        assertThat(refused).as("refused").isNotNull();
        assertThat(namedFields(refused)).containsExactly("studentPassportIssueCountryId");
    }

    @Test
    @DisplayName("null and 0 mean \"not set\": nothing is looked up and nothing is refused")
    void unsetIdsAreNotChecked() {
        JdbcTemplate untouched = mock(JdbcTemplate.class);
        StudentReferenceValidator offline = new StudentReferenceValidator();
        ReflectionTestUtils.setField(offline, "jdbcTemplate", untouched);
        List<String> columns = foreignKeyColumns();

        BiConsumer<String, Integer> check = (what, value) -> {
            StudentDatum student = new StudentDatum();
            setEveryForeignKey(student, columns, value);
            assertThatCode(() -> offline.requireExistingReferences(student)).as(what).doesNotThrowAnyException();
        };
        check.accept("every id null", null);
        check.accept("every id 0, the forms' empty choice", 0);

        verifyNoInteractions(untouched);
    }
}
