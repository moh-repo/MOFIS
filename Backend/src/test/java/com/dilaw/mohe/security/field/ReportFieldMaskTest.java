package com.dilaw.mohe.security.field;

import com.dilaw.mohe.config.MaskedAs;
import com.dilaw.mohe.config.SensitiveField;
import com.dilaw.mohe.dto.StudentProfileDocDto;
import com.dilaw.mohe.model.CustomFilterReport;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentListDetailed;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Field masking for reports and exports (plan W5-03, security plan R-18 / O-03).
 *
 * <p>Every export used to print the passport number, the national id and the
 * bank account to anyone who could reach the report, because nothing on those
 * paths goes through Jackson. These cover where a report row's rule comes from
 * and who it is decided against.
 */
class ReportFieldMaskTest {

    /** A report row shaped like the real ones: each of the three ways a rule is found, and one ordinary column. */
    static class Row {
        /** Found by name on StudentDatum. */
        String studentPassportNumber;
        /** The same datum renamed, so it says which rule applies. */
        @MaskedAs(type = StudentDatum.class, field = "studentGovId")
        String nationalNumber;
        /** Its own rule, as an entity would carry it. */
        @SensitiveField(anyOf = {"FINANCE.VIEW"})
        String payout;
        /** Nothing sensitive. */
        String universityName;
    }

    private final UmsPermissionRepository repository = mock(UmsPermissionRepository.class);

    /** @param definedCodes the field codes the administrators have catalogued; none means the anyOf fallback */
    private ReportFieldMask mask(String... definedCodes) {
        when(repository.findActiveFieldPermissionCodes()).thenReturn(List.of(definedCodes));
        return new ReportFieldMask(new FieldAccessService(repository));
    }

    private static ReportFieldMask.Viewer holding(String... codes) {
        return new ReportFieldMask.Viewer(Set.of(codes));
    }

    @Test
    @DisplayName("a row property finds its rule by name, by @MaskedAs, or on itself")
    void rulesAreResolvedThreeWays() {
        assertThat(ReportFieldMask.rulesFor(Row.class))
                .containsOnlyKeys("studentPassportNumber", "nationalNumber", "payout");
    }

    @Test
    @DisplayName("a clerk holding nothing gets none of the three, and the university name regardless")
    void withoutTheCodesEverySensitiveColumnIsHidden() {
        Set<String> hidden = mask().hiddenOn(Row.class, holding());

        assertThat(hidden).containsExactlyInAnyOrder("studentPassportNumber", "nationalNumber", "payout");
        assertThat(hidden).doesNotContain("universityName");
    }

    @Test
    @DisplayName("the codes that show a field on screen show it on the report")
    void theHolderOfTheCodeSeesIt() {
        assertThat(mask().hiddenOn(Row.class, holding("STUDENT_QUICK_EDIT.VIEW")))
                .as("the student rules name this code; the payout is finance")
                .containsExactly("payout");

        assertThat(mask().hiddenOn(Row.class, holding("STUDENT_QUICK_EDIT.VIEW", "FINANCE.VIEW")))
                .isEmpty();
    }

    @Test
    @DisplayName("a catalogued field answers to its VIEW code, not to the fallback")
    void theCatalogueWins() {
        // STUDENTS_ADD.ADD_STUDENT_ID_NUMBER.VIEW is defined, so holding the
        // annotation's fallback code is no longer enough for that one field.
        ReportFieldMask mask = mask("STUDENTS_ADD.ADD_STUDENT_ID_NUMBER.VIEW");

        assertThat(mask.hiddenOn(Row.class, holding("STUDENT_QUICK_EDIT.VIEW", "FINANCE.VIEW")))
                .containsExactly("nationalNumber");
        assertThat(mask.hiddenOn(Row.class, holding("STUDENT_QUICK_EDIT.VIEW", "FINANCE.VIEW",
                "STUDENTS_ADD.ADD_STUDENT_ID_NUMBER.VIEW")))
                .isEmpty();
    }

    @Test
    @DisplayName("a report with nothing sensitive on it is not masked at all")
    void aggregateRowsAreUntouched() {
        assertThat(ReportFieldMask.rulesFor(Plain.class)).isEmpty();
        assertThat(mask().hiddenOn(Plain.class, holding())).isEmpty();
    }

    static class Plain {
        String universityName;
        Integer studentCount;
    }

    // ── the real rows ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the student documents carry the rules of the datum they print")
    void theStudentDocumentsAreGoverned() {
        assertThat(ReportFieldMask.rulesFor(StudentProfileDocDto.class))
                .containsKeys("passportNumber", "birthDate", "birthPlace", "passportIssueDate",
                        "passportExpireDate", "email", "cellphone", "homeNumber", "emergencyContact", "amount");

        assertThat(mask().hiddenOn(StudentProfileDocDto.class, holding()))
                .as("an ID card is not a way around a field the screen hid")
                .contains("passportNumber", "birthDate", "email", "cellphone");
        assertThat(mask().hiddenOn(StudentProfileDocDto.class, holding()))
                .as("the name and the university are what the document is for")
                .doesNotContain("studentName", "studyUniversityName");
    }

    @Test
    @DisplayName("the detailed student list is governed without an annotation, by column name")
    void theDetailedListIsGovernedByName() {
        assertThat(mask().hiddenOn(StudentListDetailed.class, holding()))
                .contains("studentPassportNumber", "studentBirthdate");
    }

    @Test
    @DisplayName("the custom filter report's passport number and amount are governed")
    void theCustomFilterReportIsGoverned() {
        assertThat(mask().hiddenOn(CustomFilterReport.class, holding()))
                .contains("passportNumber", "financialAmount");
        assertThat(mask().hiddenOn(CustomFilterReport.class, holding("UMS.VIEW", "FINANCE.VIEW")))
                .isEmpty();
    }

    @Test
    @DisplayName("RPT-01: the custom filter report's passport column answers to the passport rule, not the national id's")
    void theCustomFilterPassportUsesThePassportRule() {
        FieldAccessService.Rule passport = FieldAccessService.rulesOf(StudentDatum.class).get("studentPassportNumber");
        FieldAccessService.Rule onRow = ReportFieldMask.rulesFor(CustomFilterReport.class).get("passportNumber");

        assertThat(onRow).as("the column is the passport number, and is named for it").isNotNull();
        assertThat(onRow.viewCode()).isEqualTo(passport.viewCode());
        assertThat(onRow.anyOf()).containsExactly(passport.anyOf());
        assertThat(ReportFieldMask.rulesFor(CustomFilterReport.class)).doesNotContainKey("nationalNumber");

        // Catalogued: the national-id VIEW code no longer opens the passport column.
        ReportFieldMask mask = mask("STUDENTS_ADD.ADD_STUDENT_ID_NUMBER.VIEW", "STUDENTS_ADD.ADD_STUDENT_PASSPORT.VIEW");
        assertThat(mask.hiddenOn(CustomFilterReport.class, holding("STUDENTS_ADD.ADD_STUDENT_ID_NUMBER.VIEW", "FINANCE.VIEW")))
                .containsExactly("passportNumber");
        assertThat(mask.hiddenOn(CustomFilterReport.class, holding("STUDENTS_ADD.ADD_STUDENT_PASSPORT.VIEW", "FINANCE.VIEW")))
                .isEmpty();
    }

    // ── the map path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a column the query builder selects finds its rule through the entity's @Column")
    void columnsAreGovernedToo() {
        assertThat(ReportFieldMask.ruleForColumn("STUDENT_DATA", "STUDENT_PASSPORT_NUMBER")).isNotNull();
        assertThat(ReportFieldMask.ruleForColumn("student_data", "student_passport_number"))
                .as("the query builder does not promise a case").isNotNull();
        assertThat(ReportFieldMask.ruleForColumn("STUDENT_FINANCE", "STUDENT_ACCOUNT_NUMBER")).isNotNull();
        assertThat(ReportFieldMask.ruleForColumn("STUDENT_DATA", "STUDENT_NAME")).isNull();

        assertThat(mask().mayViewColumn("STUDENT_DATA", "STUDENT_PASSPORT_NUMBER", holding())).isFalse();
        assertThat(mask().mayViewColumn("STUDENT_DATA", "STUDENT_PASSPORT_NUMBER", holding("UMS.VIEW"))).isTrue();
        assertThat(mask().mayViewColumn("STUDENT_DATA", "STUDENT_NAME", holding())).isTrue();
    }

    // ── who is looking ───────────────────────────────────────────────────────

    @Test
    @DisplayName("an owner who cannot be resolved holds nothing, so the export is masked and not widened")
    void anUnresolvableOwnerSeesNothing() {
        assertThat(mask().viewerFor(null).held()).isEmpty();
        assertThat(mask().viewerFor(7L).held())
                .as("no authority cache outside Spring: fail closed, not open")
                .isEmpty();
    }
}
