package com.dilaw.mohe.config;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A table that carries a {@code CODE} and an entity that does not know about it.
 *
 * <p>The code lists own their catalogue since V072: a lookup row's stable key is
 * its {@code CODE}, Java constants name rows by it, and eleven report views split
 * their columns on it. The Lookup Tables screen writes these rows through the JPA
 * entities. So an entity that does not map the column its table has is not a
 * cosmetic gap — the screen cannot show the code, cannot set it, and every row
 * added there is written with {@code CODE NULL}, silently, into a column the
 * application reads.
 *
 * <p>That is not hypothetical. {@code STUDENT_GENDER} gained a {@code CODE} in
 * V075 because the gender split reads it; {@link com.dilaw.mohe.model.StudentGender}
 * did not map it, and a row added through the screen on 31 August 2026 carried
 * {@code NULL}. {@code ReportViewsReadWhatIsRecordedTest} failed on it for two
 * days and named the symptom rather than the cause. Nothing would have caught the
 * next one.
 *
 * <p>So: walk the live metamodel, ask the catalogue which of those tables have a
 * {@code CODE} column, and fail when an entity does not map it. The metamodel is
 * used rather than the source text because the twenty-odd {@code LU_*} entities
 * inherit the column from {@code AbstractLuLookup} and a text scan calls every
 * one of them a defect.
 *
 * <p>{@link #CODE_IS_THE_SENTINEL_ONLY} is the honest remainder: three tables
 * where a code is deliberately not something an administrator sets. The assertion
 * is equality, so this fails both ways — when a new unmapped code column appears,
 * and when a listed one is mapped without being pruned here.
 */
@Tag("integration")
@SpringBootTest
class CodeColumnMappingAgreementTest {

    /**
     * Tables whose {@code CODE} is deliberately not mapped, each with its reason.
     *
     * <p>All three are read through {@code CodeListRegistry} with an explicit
     * {@code WHERE CODE IS NOT NULL}, so an uncoded row is not a broken row — it
     * is a row the code list does not offer, which is what these tables want.
     *
     * <ul>
     *   <li>{@code STUDENT_UNIVERSITY_TYPE} and {@code STUDENT_MINISTRY_TYPE} —
     *       V072 coded only the "not specified" sentinel (id 99) on each, on
     *       purpose: {@code UniversityTypeCode} and {@code MinistryTypeCode} name
     *       that row and nothing else. The other rows are free text an attaché
     *       maintains, and giving the screen a code box would invite codes no
     *       constant reads.</li>
     *   <li>{@code STUDY_PAY_FEE_IN_STATUS} — one coded row, the cancelled status
     *       {@code StudyUniversityFeeService} excludes. The list is not on the
     *       Lookup Tables screen at all, so nothing can add an uncoded row
     *       through the UI.</li>
     * </ul>
     *
     * <p>Map the column and prune the entry the day one of these needs a second
     * code. Shrink this list; never grow it without a reason written here.
     */
    private static final Set<String> CODE_IS_THE_SENTINEL_ONLY = Set.of(
            "MOHECORE.STUDENT_MINISTRY_TYPE",
            "MOHECORE.STUDENT_UNIVERSITY_TYPE",
            "MOHECORE.STUDY_PAY_FEE_IN_STATUS");

    /** {@code hibernate.default_schema} in application.properties. */
    private static final String DEFAULT_SCHEMA = "MOHECORE";

    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("every table with a CODE column has an entity that maps it, except the recorded remainder")
    void codeColumnsAreMapped() {
        Set<String> unmapped = new TreeSet<>();
        int checked = 0;

        for (EntityType<?> entity : emf.getMetamodel().getEntities()) {
            // @Table is not @Inherited: for a mapped superclass walk up to it.
            Table table = null;
            for (Class<?> c = entity.getJavaType(); c != null && table == null; c = c.getSuperclass()) {
                table = c.getAnnotation(Table.class);
            }
            if (table == null || table.name().isEmpty()) continue;   // @Subselect views and the like
            String schema = table.schema().isEmpty() ? DEFAULT_SCHEMA : table.schema();
            String qualified = schema + "." + table.name();

            Integer hasCode = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(?) AND name = 'CODE'",
                    Integer.class, qualified);
            if (hasCode == null || hasCode == 0) continue;

            checked++;
            if (!mapsCodeColumn(entity)) unmapped.add(qualified);
        }

        assertThat(checked)
                .as("the metamodel walk found the tables carrying a CODE column")
                .isGreaterThan(20);
        assertThat(unmapped)
                .as("""
                    These tables have a CODE column their entity does not map, so the Lookup \
                    Tables screen cannot set one and every row added through it is written \
                    NULL — into a column Java constants and the report views read. Map the \
                    column (see StudentGender), or add it to CODE_IS_THE_SENTINEL_ONLY with \
                    the reason a code is not the administrator's to set.""")
                .isEqualTo(new TreeSet<>(CODE_IS_THE_SENTINEL_ONLY));
    }

    /** Whether any persistent attribute of this entity is mapped to the CODE column. */
    private static boolean mapsCodeColumn(EntityType<?> entity) {
        for (Attribute<?, ?> attribute : entity.getAttributes()) {
            Member member = attribute.getJavaMember();
            if (!(member instanceof AnnotatedElement annotated)) continue;
            Column column = annotated.getAnnotation(Column.class);
            String name = column != null && !column.name().isEmpty() ? column.name() : attribute.getName();
            if ("CODE".equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
