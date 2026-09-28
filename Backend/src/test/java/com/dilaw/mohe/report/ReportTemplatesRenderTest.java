package com.dilaw.mohe.report;

import com.dilaw.mohe.model.ReportTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The saved-query listing renders with a row in it.
 *
 * <p>Every other render test fills with {@code Collections.emptyList()}, which
 * proves the masthead and the footer but never resolves a single detail field: a
 * {@code <field>} the bean does not supply costs nothing until there is a row to
 * read it from. That is not a hypothetical gap here — V106 removed
 * {@code template_status} from the view, the entity and the screen and left the
 * template printing {@code $F&#123;templateStatus&#125;}, and nothing failed, because
 * {@code V_Report_Template} reads {@code SAVED_QUERY} and dev has no saved
 * queries. The report would have broken for the first user who saved one.
 *
 * <p>So this test fills with a populated row on purpose. It is the only report
 * whose view is empty on every environment until a user acts, which is exactly
 * why it needs the row supplied by hand.
 */
class ReportTemplatesRenderTest {

    private static ReportTemplate row() {
        ReportTemplate t = new ReportTemplate();
        t.setTemplateId(1L);
        t.setTemplateName("طلاب اليمن");
        t.setReportType("STUDENT_LIST");
        t.setEstimatedRecords(941);
        t.setUsageCount(7);
        t.setIsPublic(1);
        return t;
    }

    private static Map<String, Object> params(boolean rtl) {
        Map<String, Object> p = new HashMap<>();
        p.put("IS_RTL", rtl);
        return SeededReportLabels.putAll(p, "REPORT_TEMPLATES", rtl);
    }

    @Test
    @DisplayName("the listing fills with a row rather than failing on a field no bean supplies")
    void fillsWithARow() throws Exception {
        String text = ReportRenderSupport.renderToText("report-templates", params(true), List.of(row()));

        assertThat(text)
                .as("the row's own values must reach the page")
                .contains("طلاب اليمن")
                .contains("STUDENT_LIST");
    }

    @Test
    @DisplayName("the third column prints a value the bean actually carries")
    void thirdColumnIsRecorded() throws Exception {
        Map<String, Integer> positions =
                ReportRenderSupport.textPositions("report-templates", params(true), List.of(row()));

        // Read right to left: name, then type, then the third column. Asserted
        // as an ordering rather than as coordinates, because the printed x
        // carries the page margin and the margin is nobody's business here.
        // Whatever occupies that third column has to come from the row: it held
        // templateStatus, which no longer exists anywhere below the template.
        assertThat(positions)
                .as("usage count is what V106 started recording, and what the screen shows here")
                .containsKey("7");
        assertThat(positions.get("7"))
                .as("the count is the leftmost of the three columns, where the status was")
                .isLessThan(positions.get("STUDENT_LIST"))
                .isLessThan(positions.get("طلاب اليمن"));
    }
}
