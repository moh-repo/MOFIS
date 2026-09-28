package com.dilaw.mohe.util;

import com.dilaw.mohe.controler.LookupNamesAdvice;
import com.dilaw.mohe.controler.LookupTableController;
import com.dilaw.mohe.model.LuTrendClass;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live QA M11 (25 Sep 2026): the lookup API saved rows with blank names on all 33
 * tables. Decision D20-a: the Arabic and the English name are both required, except
 * on report labels (P5-6), which store a missing English name as "" (R5-24).
 */
class LookupNamesRequiredTest {

    private static LuTrendClass lu(String ar, String en) {
        LuTrendClass c = new LuTrendClass();
        c.setCode("X");
        c.setNameAr(ar);
        c.setNameEn(en);
        return c;
    }

    private static StudentDegree degree(String ar, String en) {
        StudentDegree d = new StudentDegree();
        d.setStudentDegreeName(ar);
        d.setStudentDegreeNameEn(en);
        return d;
    }

    @Test
    @DisplayName("an LU table: a blank English or Arabic name is refused, both present pass")
    void luTable() {
        assertThatThrownBy(() -> LookupNamesRequired.require(lu("اسم", " "))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the English name");
        assertThatThrownBy(() -> LookupNamesRequired.require(lu(null, "Name"))).hasMessageContaining("the Arabic name");
        assertThatCode(() -> LookupNamesRequired.require(lu("اسم", "Name"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a legacy table: <x>Name and <x>NameEn are found by their shape")
    void legacyTable() {
        assertThatThrownBy(() -> LookupNamesRequired.require(degree("", ""))).hasMessageContaining("the Arabic name")
                .hasMessageContaining("the English name");
        assertThatCode(() -> LookupNamesRequired.require(degree("بكالوريوس", "Bachelor"))).doesNotThrowAnyException();
        StudyStatus s = new StudyStatus();
        s.setStatusName("منتظم");
        assertThatThrownBy(() -> LookupNamesRequired.require(s)).hasMessageContaining("the English name");
    }

    @Test
    @DisplayName("P5-6: a report label may leave the English name blank (LBL_YEAR_SUFFIX), not the Arabic one")
    void reportLabelEnglishOptional() {
        com.dilaw.mohe.model.LuReportLabel suffix = new com.dilaw.mohe.model.LuReportLabel();
        suffix.setCode("LBL_YEAR_SUFFIX");
        suffix.setNameAr("م");
        suffix.setNameEn("");
        assertThatCode(() -> LookupNamesRequired.require(suffix)).doesNotThrowAnyException();

        suffix.setNameAr(" ");
        assertThatThrownBy(() -> LookupNamesRequired.require(suffix)).hasMessageContaining("the Arabic name")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("the English name"));

        // The rule is the table's: any other LU table still needs both names.
        assertThatThrownBy(() -> LookupNamesRequired.require(lu("م", ""))).hasMessageContaining("the English name");
        assertThat(LookupNamesRequired.englishOptional(com.dilaw.mohe.model.LuReportLabel.class)).isTrue();
        assertThat(LookupNamesRequired.englishOptional(LuTrendClass.class)).isFalse();
    }

    @Test
    @DisplayName("R5-24: a report label saved with no English name stores \"\", as NAME_EN is NOT NULL")
    void reportLabelMissingEnglishStoredEmpty() throws Exception {
        Method callback = Arrays.stream(com.dilaw.mohe.model.LuReportLabel.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(jakarta.persistence.PrePersist.class))
                .findFirst().orElseThrow(() -> new AssertionError("LuReportLabel has no @PrePersist"));
        assertThat(callback.isAnnotationPresent(jakarta.persistence.PreUpdate.class))
                .as("an edit that drops the English name is stored the same way").isTrue();
        callback.setAccessible(true);

        com.dilaw.mohe.model.LuReportLabel label = new com.dilaw.mohe.model.LuReportLabel();
        label.setCode("LBL_NEW");
        label.setNameAr("م");
        callback.invoke(label);
        assertThat(label.getNameEn()).isEmpty();

        label.setNameEn("Year");
        callback.invoke(label);
        assertThat(label.getNameEn()).isEqualTo("Year");
    }

    @Test
    @DisplayName("P5-6: the Code Lists screen's create follows the same table rule")
    void codeListCreateFollowsTheTable() {
        java.lang.reflect.Method validate = java.util.Arrays.stream(
                        com.dilaw.mohe.service.codelist.CodeListService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("validate")).findFirst().orElseThrow();
        validate.setAccessible(true);
        var blankEnglish = new com.dilaw.mohe.service.codelist.CodeListService.CodeRowInput(
                "LBL_X", "م", "", null, null, null, true, null);
        assertThatCode(() -> validate.invoke(null, blankEnglish, true, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validate.invoke(null, blankEnglish, true, false))
                .hasRootCauseInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    @DisplayName("a body that is not a lookup row is left alone")
    void otherBodies() {
        assertThatCode(() -> LookupNamesRequired.require(java.util.Map.of("a", 1))).doesNotThrowAnyException();
        assertThatCode(() -> LookupNamesRequired.require(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the advice runs the check on every body the lookup controller reads")
    void adviceChecksTheBody() {
        LookupNamesAdvice advice = new LookupNamesAdvice();
        assertThatThrownBy(() -> advice.afterBodyRead(degree("", "x"), null, null, StudentDegree.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("every create and update of the lookup controller takes a type with two name fields (all 33 tables)")
    void everyLookupBodyHasNames() {
        Set<String> without = new TreeSet<>();
        Set<String> types = new TreeSet<>();
        for (Method m : LookupTableController.class.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(PostMapping.class) && !m.isAnnotationPresent(PutMapping.class)) continue;
            for (Parameter p : m.getParameters()) {
                if (!p.isAnnotationPresent(RequestBody.class)) continue;
                types.add(p.getType().getSimpleName());
                if (LookupNamesRequired.nameFields(p.getType()).size() != 2) without.add(p.getType().getSimpleName());
            }
        }
        assertThat(types).hasSizeGreaterThanOrEqualTo(33);
        assertThat(without).as("lookup bodies whose names the check cannot find").isEmpty();
    }
}
