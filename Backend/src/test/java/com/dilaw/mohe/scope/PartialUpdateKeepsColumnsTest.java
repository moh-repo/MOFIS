package com.dilaw.mohe.scope;

import com.dilaw.mohe.dto.StudyWafarTypeRequest;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.model.StudyWafarType;
import com.dilaw.mohe.service.StudyFinanceBankBranchService;
import com.dilaw.mohe.service.StudyFinanceBankService;
import com.dilaw.mohe.service.StudyWafarTypeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An edit changes what it names and leaves the rest of the row alone.
 *
 * <p>Banks, bank branches and wafar types used to save the <b>request body</b> over
 * the stored row. The three edit dialogs send four fields and never the English
 * name, so every save wrote NULL over {@code STUDY_FINANCE_BANKE_NAME_EN},
 * {@code STUDY_FINANCE_BANK_BRANCH_NAME_EN} and {@code WAFAR_NAME_EN} — and the
 * banks' {@code isActive} field initialiser ({@code = true}) meant a request that
 * never mentioned the flag switched a disabled bank back on. Nobody had edited one
 * on the dev database yet, so nothing was lost; the defect was in the path, not in
 * the data.
 *
 * <p>What is asserted is the distinction the fix rests on: <b>a field that is
 * absent is not a field set to nothing.</b> Each case renames a row through the
 * service with everything else left null, then re-reads and expects the untouched
 * columns to be exactly as they were.
 *
 * <p>Every row is created by the test and removed in a {@code finally}; no
 * pre-existing row is written to.
 */
@Tag("integration")
@SpringBootTest
class PartialUpdateKeepsColumnsTest {

    @Autowired private StudyFinanceBankService banks;
    @Autowired private StudyFinanceBankBranchService branches;
    @Autowired private StudyWafarTypeService wafarTypes;

    /** Egypt: the attaché the seeded banks belong to, so the scope check passes. */
    private static final int EGYPT = 1;

    /** hr_manager: an Egyptian (country-tier) account, the one S-06 lets write Egypt's branches. */
    private static final long EGYPT_OFFICER = 3L;

    private static String marker() {
        return "PU-TEST " + UUID.randomUUID();
    }

    @Test
    @DisplayName("renaming a bank keeps its English name and its active flag")
    void bankKeepsWhatTheEditDoesNotMention() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            Integer id = null;
            try {
                StudyFinanceBank draft = new StudyFinanceBank();
                draft.setStudyFinanceBankeName(marker());
                draft.setStudyFinanceBankeNameEn("Keep This English Name");
                draft.setIsActive(false);
                draft.setStudyCountryInId(EGYPT);
                id = banks.create(draft).getId();

                // What the dialog actually sends on an edit: no English name.
                StudyFinanceBank edit = new StudyFinanceBank();
                edit.setStudyFinanceBankeName(marker());
                edit.setStudyCountryInId(EGYPT);
                banks.update(id, edit);

                StudyFinanceBank after = banks.getById(id);
                assertThat(after.getStudyFinanceBankeNameEn())
                        .as("the English name the edit never mentioned")
                        .isEqualTo("Keep This English Name");
                assertThat(after.getIsActive())
                        .as("a disabled bank must not switch itself back on")
                        .isFalse();
            } finally {
                if (id != null) { banks.delete(id); }
            }
        });
    }

    @Test
    @DisplayName("renaming a branch keeps its English name and its active flag")
    void branchKeepsWhatTheEditDoesNotMention() {
        // S-06: a branch is the attaché's own configuration, so the branch itself is
        // created and edited by an Egyptian officer; the bank stays ministry data.
        Integer bankId = null;
        Integer branchId = null;
        try {
            StudyFinanceBank bank = new StudyFinanceBank();
            bank.setStudyFinanceBankeName(marker());
            bank.setStudyCountryInId(EGYPT);
            bankId = AsUser.call(AsUser.SUPERADMIN, () -> banks.create(bank)).getId();
            final Integer bankRef = bankId;

            branchId = AsUser.call(EGYPT_OFFICER, () -> {
                StudyFinanceBankBranch draft = new StudyFinanceBankBranch();
                draft.setStudyFinanceBankBranchName(marker());
                draft.setStudyFinanceBankBranchNameEn("Keep This Branch Name");
                draft.setIsActive(false);
                draft.setStudyFinanceBankeId(bankRef);
                draft.setStudyCountryInId(EGYPT);
                return branches.create(draft).getId();
            });
            final Integer branchRef = branchId;

            AsUser.run(EGYPT_OFFICER, () -> {
                StudyFinanceBankBranch edit = new StudyFinanceBankBranch();
                edit.setStudyFinanceBankBranchName(marker());
                edit.setStudyFinanceBankeId(bankRef);
                branches.update(branchRef, edit);

                StudyFinanceBankBranch after = branches.getById(branchRef);
                assertThat(after.getStudyFinanceBankBranchNameEn())
                        .as("the English name the edit never mentioned")
                        .isEqualTo("Keep This Branch Name");
                assertThat(after.getIsActive())
                        .as("a disabled branch must not switch itself back on")
                        .isFalse();
                assertThat(after.getStudyCountryInId())
                        .as("the branch still belongs to its bank's attaché")
                        .isEqualTo(EGYPT);
            });
        } finally {
            final Integer branchRef = branchId;
            final Integer bankRef = bankId;
            if (branchRef != null) { AsUser.run(EGYPT_OFFICER, () -> branches.delete(branchRef)); }
            if (bankRef != null) { AsUser.run(AsUser.SUPERADMIN, () -> banks.delete(bankRef)); }
        }
    }

    @Test
    @DisplayName("renaming a wafar type keeps its English name and its note")
    void wafarTypeKeepsWhatTheEditDoesNotMention() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            Integer id = null;
            try {
                StudyWafarTypeRequest create = new StudyWafarTypeRequest();
                create.setWafarName(marker());
                create.setWafarNameEn("Keep This Wafar Name");
                create.setWafarNote1("Keep this note");
                id = wafarTypes.createFromRequest(create).getId();

                // The dialog sends the Arabic name only.
                StudyWafarTypeRequest edit = new StudyWafarTypeRequest();
                edit.setWafarName(marker());
                wafarTypes.updateFromRequest(id, edit);

                StudyWafarType after = wafarTypes.getById(id);
                assertThat(after.getWafarNameEn())
                        .as("the English name the edit never mentioned")
                        .isEqualTo("Keep This Wafar Name");
                assertThat(after.getWafarNote1())
                        .as("the note the edit never mentioned")
                        .isEqualTo("Keep this note");
            } finally {
                if (id != null) { wafarTypes.delete(id); }
            }
        });
    }

    @Test
    @DisplayName("an edit that does name a field still changes it")
    void whatTheEditDoesMentionIsWritten() {
        // The positive control: the guards must not turn update into a no-op.
        AsUser.run(AsUser.SUPERADMIN, () -> {
            Integer id = null;
            try {
                StudyFinanceBank draft = new StudyFinanceBank();
                draft.setStudyFinanceBankeName(marker());
                draft.setStudyFinanceBankeNameEn("Before");
                draft.setStudyCountryInId(EGYPT);
                id = banks.create(draft).getId();

                StudyFinanceBank edit = new StudyFinanceBank();
                edit.setStudyFinanceBankeName("Renamed");
                edit.setStudyFinanceBankeNameEn("After");
                edit.setIsActive(false);
                edit.setStudyCountryInId(EGYPT);
                banks.update(id, edit);

                StudyFinanceBank after = banks.getById(id);
                assertThat(after.getStudyFinanceBankeName()).isEqualTo("Renamed");
                assertThat(after.getStudyFinanceBankeNameEn()).isEqualTo("After");
                assertThat(after.getIsActive()).isFalse();
            } finally {
                if (id != null) { banks.delete(id); }
            }
        });
    }

    @Test
    @DisplayName("a created row is active unless the request says otherwise")
    void createDefaultsTheActiveFlag() {
        // Removing the field initialiser is what lets update tell "absent" from
        // "false"; this is the other half of that change.
        AsUser.run(AsUser.SUPERADMIN, () -> {
            Integer id = null;
            try {
                StudyFinanceBank draft = new StudyFinanceBank();
                draft.setStudyFinanceBankeName(marker());
                draft.setStudyCountryInId(EGYPT);
                id = banks.create(draft).getId();
                assertThat(banks.getById(id).getIsActive())
                        .as("a new bank with no flag stated is active")
                        .isTrue();
            } finally {
                if (id != null) { banks.delete(id); }
            }
        });
    }
}
