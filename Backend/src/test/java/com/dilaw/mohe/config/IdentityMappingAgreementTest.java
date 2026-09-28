package com.dilaw.mohe.config;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
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
 * Every {@code @GeneratedValue(IDENTITY)} mapping points at a real identity column.
 *
 * <p>The schema here is hand-run scripts with {@code ddl-auto=none}, so nothing
 * ever checks the entities against the database — which is how 22 lookup
 * entities shipped with {@code IDENTITY} on columns the baseline created as
 * {@code int NOT NULL DEFAULT ((0))}, and Add crashed with
 * {@code AssertionFailure: null identifier} on every one of them
 * (fixed 30 Aug 2026).
 * This test walks the live JPA metamodel and asks {@code COLUMNPROPERTY} whether
 * each mapped identity column really is one.
 *
 * <p>{@link #KNOWN_MISMATCHES} is the honest remainder: entities with the same
 * defect that are <b>not reachable from the Administration menu</b> and were left
 * out of that fix on purpose. The assertion is equality, not containment, so this
 * fails both ways — when a new mismatch appears, and when a listed one is fixed
 * without being pruned here. Shrink the list; never grow it.
 */
@Tag("integration")
@SpringBootTest
class IdentityMappingAgreementTest {

    /**
     * Same defect, other menus: create is broken on these today. Each needs the
     * {@code nextId()} treatment (or a DDL decision) when its page comes up.
     */
    private static final Set<String> KNOWN_MISMATCHES = Set.of(
            "MOHECORE.STUDENT_ACCOUNT.STUDENT_ID",
            "MOHECORE.STUDENT_EDUCTION.STUD_EDUCTION_ID",
            "MOHECORE.STUDENT_MINISTRY_SUB_TYPE.STUDENT_MINISTRY_SUB_TYPE_ID",
            "MOHECORE.STUDENT_QUARTER.STUDY_FINANCE_DOF_ID",
            "MOHECORE.STUDY_FIN_RECEIVED_STATUS.STUDY_FIN_RECEIVED_STATUS_ID",
            "MOHECORE.STUDY_FIN_RECEIVED_WHOM_STATUS.STUDY_FIN_RECEIVED_WHOM_STATUS_ID",
            "MOHECORE.STUDY_FINANCE_DOF.STUDY_FINANCE_DOF_ID",
            "MOHECORE.STUDY_FINANCE_DOF_YEAR.STUDY_FINANCE_DOF_YEAR_ID",
            "MOHECORE.STUDY_PAY_FEE_IN_STATUS.STUDY_PAY_FEE_IN_STATUS_ID");

    /** {@code hibernate.default_schema} in application.properties. */
    private static final String DEFAULT_SCHEMA = "MOHECORE";

    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("IDENTITY mappings and identity columns agree, except the recorded remainder")
    void mappingsAgreeWithTheDatabase() {
        Set<String> mismatches = new TreeSet<>();
        Set<String> unresolvable = new TreeSet<>();
        int checked = 0;

        for (EntityType<?> entity : emf.getMetamodel().getEntities()) {
            SingularAttribute<?, ?> id = entity.getSingularAttributes().stream()
                    .filter(SingularAttribute::isId).findFirst().orElse(null);
            if (id == null) continue; // composite ids carry no @GeneratedValue

            Member member = id.getJavaMember();
            if (!(member instanceof AnnotatedElement annotated)) continue;
            GeneratedValue gen = annotated.getAnnotation(GeneratedValue.class);
            if (gen == null || gen.strategy() != GenerationType.IDENTITY) continue;

            // @Table is not @Inherited: for SINGLE_TABLE subclasses walk up to it.
            Class<?> type = entity.getJavaType();
            Table table = null;
            for (Class<?> c = type; c != null && table == null; c = c.getSuperclass()) {
                table = c.getAnnotation(Table.class);
            }
            String tableName = table != null && !table.name().isEmpty() ? table.name() : entity.getName();
            String schema = table != null && !table.schema().isEmpty() ? table.schema() : DEFAULT_SCHEMA;

            Column column = annotated.getAnnotation(Column.class);
            String columnName = column != null && !column.name().isEmpty() ? column.name() : member.getName();

            Integer isIdentity = jdbc.queryForObject(
                    "SELECT COLUMNPROPERTY(OBJECT_ID(?), ?, 'IsIdentity')",
                    Integer.class, schema + "." + tableName, columnName);

            String key = schema + "." + tableName + "." + columnName;
            checked++;
            if (isIdentity == null) {
                unresolvable.add(key + " (" + type.getSimpleName() + ": table or column not found)");
            } else if (isIdentity == 0) {
                mismatches.add(key);
            }
        }

        assertThat(checked).as("the metamodel walk found the IDENTITY mappings").isGreaterThan(80);
        assertThat(unresolvable)
                .as("every IDENTITY-mapped table and column must exist in this database")
                .isEmpty();
        assertThat(mismatches)
                .as("IDENTITY mapped on a non-identity column: create throws 'null identifier' on these. "
                        + "Fix with the assigned-id idiom (repository.nextId(), see StudyFinanceBankService) "
                        + "and prune KNOWN_MISMATCHES when one is repaired.")
                .isEqualTo(new TreeSet<>(KNOWN_MISMATCHES));
    }
}
