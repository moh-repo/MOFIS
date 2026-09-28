package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.StudentGraphNodeDto;
import com.dilaw.mohe.service.StudentGraphService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Builds the student graph from live database rows.
 *
 * <p>Country scope is stubbed: it resolves a UMS principal from the JWT, which
 * a test cannot supply, and is not what these assertions are about.
 */
@Tag("integration")
@SpringBootTest
class StudentGraphServiceTest {

    /** Has finance, fee, books, loan, extension and payment rows. */
    private static final int STUDENT_ID = 4;

    /**
     * Labels whose value is a name resolved from a lookup. The page previously
     * assembled these in the browser and fell back to printing the raw numeric
     * id when a lookup row was missing, so a digits-only value here means that
     * regression is back.
     */
    private static final Set<String> NAME_LABELS = Set.of(
            "Gender", "Country", "Governorate", "District", "Issuing Country",
            "Degree", "Major", "Current Level", "Study Type", "University Type",
            "Agency Type", "Ministry / Entity", "Country of Study", "City / Region",
            "University", "College", "Department", "Specialization",
            "Currency", "Bank", "Branch", "Status", "Wafar Type");

    @Autowired
    private StudentGraphService graphService;

    @MockBean
    private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("graph expands at least three levels and covers more than the original six categories")
    void graph_hasDepthAndBreadth() {
        StudentGraphNodeDto root = graphService.buildGraph(STUDENT_ID, "en", true);

        assertThat(root.getLabel()).as("root is the student").isNotBlank();
        assertThat(root.getChildren().size())
                .as("expected more categories than the six the page shipped with")
                .isGreaterThan(6);
        assertThat(depth(root))
                .as("student -> category -> record -> detail is four levels; the page used to stop at three")
                .isGreaterThanOrEqualTo(4);

        // A repeating record must itself be expandable, which is the "more steps" requirement.
        boolean hasGrandchildRecord = root.getChildren().stream()
                .flatMap(c -> c.getChildren().stream())
                .anyMatch(StudentGraphNodeDto::hasChildren);
        assertThat(hasGrandchildRecord)
                .as("at least one record (finance quarter, payment, fee...) expands into its own details")
                .isTrue();
    }

    @Test
    @DisplayName("no lookup-backed node falls back to printing a raw id")
    void graph_resolvesLookupNames() {
        StudentGraphNodeDto root = graphService.buildGraph(STUDENT_ID, "en", true);
        for (StudentGraphNodeDto n : flatten(root)) {
            if (n.getValue() == null || !NAME_LABELS.contains(n.getLabel())) {
                continue;
            }
            assertThat(n.getValue())
                    .as("'%s' should be a resolved name but looks like a raw id", n.getLabel())
                    .doesNotMatch("\\d+");
        }
    }

    @Test
    @DisplayName("labels follow the requested language")
    void graph_isLocalized() {
        List<String> en = flatten(graphService.buildGraph(STUDENT_ID, "en", true))
                .stream().map(StudentGraphNodeDto::getLabel).toList();
        List<String> ar = flatten(graphService.buildGraph(STUDENT_ID, "ar", true))
                .stream().map(StudentGraphNodeDto::getLabel).toList();

        assertThat(en).isNotEqualTo(ar);
        assertThat(en).contains("Personal Information");
        assertThat(ar).contains("البيانات الشخصية");
    }

    @Test
    @DisplayName("finance branches are withheld from a caller without finance permission")
    void graph_omitsFinanceWhenNotPermitted() {
        List<String> withFinance = categoryIds(graphService.buildGraph(STUDENT_ID, "en", true));
        List<String> without = categoryIds(graphService.buildGraph(STUDENT_ID, "en", false));

        assertThat(withFinance).contains("cat_finance");
        assertThat(without)
                .as("money branches must be absent, not present-and-empty")
                .doesNotContain("cat_finance", "cat_payments", "cat_fees", "cat_books", "cat_loans");
        assertThat(without)
                .as("the academic side still renders")
                .contains("cat_personal", "cat_education");
    }

    @Test
    @DisplayName("every node gets a stable id for the client to address")
    void assignIds_populatesEveryNode() {
        StudentGraphNodeDto root = graphService.buildGraph(STUDENT_ID, "en", true);
        StudentGraphService.assignIds(root);

        List<StudentGraphNodeDto> all = flatten(root);
        assertThat(all).allSatisfy(n -> assertThat(n.getId()).isNotBlank());
        assertThat(all.stream().map(StudentGraphNodeDto::getId).distinct().count())
                .as("ids must be unique or vis-network silently drops duplicates")
                .isEqualTo(all.size());
    }

    /** Set -DgraphTest.print=true to print the tree for a visual check. */
    @Test
    @DisplayName("print the built tree (opt-in diagnostics)")
    void printTree() {
        if (!Boolean.getBoolean("graphTest.print")) {
            return;
        }
        StudentGraphNodeDto root = graphService.buildGraph(STUDENT_ID, "en", true);
        StudentGraphService.assignIds(root);
        print(root, 0);
    }

    private static void print(StudentGraphNodeDto n, int indent) {
        String value = n.getValue() != null ? " = " + n.getValue() : "";
        System.out.println("  ".repeat(indent) + n.getLabel() + value);
        n.getChildren().forEach(c -> print(c, indent + 1));
    }

    private static List<String> categoryIds(StudentGraphNodeDto root) {
        return root.getChildren().stream().map(StudentGraphNodeDto::getCategory).toList();
    }

    private static int depth(StudentGraphNodeDto node) {
        int best = 0;
        for (StudentGraphNodeDto c : node.getChildren()) {
            best = Math.max(best, depth(c));
        }
        return best + 1;
    }

    private static List<StudentGraphNodeDto> flatten(StudentGraphNodeDto root) {
        List<StudentGraphNodeDto> out = new ArrayList<>();
        List<StudentGraphNodeDto> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            StudentGraphNodeDto n = stack.remove(stack.size() - 1);
            out.add(n);
            stack.addAll(n.getChildren());
        }
        return out;
    }
}
