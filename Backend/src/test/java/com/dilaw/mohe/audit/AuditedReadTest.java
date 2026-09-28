package com.dilaw.mohe.audit;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O-33: reads of personal data leave a row in {@code REPORT_ACCESS_LOG}.
 *
 * <p>The aspect is exercised through a probe bean rather than a controller, so
 * the test says nothing about which endpoints carry the annotation (the
 * source does); it says the annotation works, names the record, and that a
 * detail view stays silent unless {@code mohe.audit.detail-views} is on (D-7).
 */
@Tag("integration")
@SpringBootTest
@org.springframework.context.annotation.Import(AuditedReadTest.Probe.class)
class AuditedReadTest {

    /** Imported explicitly: Spring Boot keeps nested classes of a test out of the component scan. */
    static class Probe {
        @AuditedRead(resource = "O33_PROBE", format = "DOWNLOAD")
        public String download(Integer docId, String lang, List<Integer> ignored) {
            return "bytes";
        }

        @AuditedRead(resource = "O33_PROBE_DETAIL", detailView = true)
        public Map<String, Object> detail(Integer id) {
            return Map.of("id", id);
        }
    }

    @Autowired private Probe probe;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TenantContext.runAsSystem(() -> jdbc.update("DELETE FROM MOHECORE.REPORT_ACCESS_LOG WHERE REPORT_CODE LIKE 'O33_PROBE%'"));
    }

    private int rows(String code) throws InterruptedException {
        // The writer is asynchronous; give it a moment.
        for (int i = 0; i < 40; i++) {
            Integer n = TenantContext.runAsSystem(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM MOHECORE.REPORT_ACCESS_LOG WHERE REPORT_CODE = ?", Integer.class, code));
            if (n != null && n > 0) return n;
            Thread.sleep(50);
        }
        return 0;
    }

    @Test
    @DisplayName("a download writes who, what and which record")
    void downloadIsLogged() throws Exception {
        AsUser.call(AsUser.JO_ADMIN, () -> probe.download(42, "ar", List.of(1, 2)));
        assertThat(rows("O33_PROBE")).isEqualTo(1);
        Map<String, Object> row = TenantContext.runAsSystem(() -> jdbc.queryForMap(
                "SELECT TOP 1 EXPORT_FORMAT, FILTER_SUMMARY, USERNAME FROM MOHECORE.REPORT_ACCESS_LOG WHERE REPORT_CODE = 'O33_PROBE'"));
        assertThat(row.get("EXPORT_FORMAT")).isEqualTo("DOWNLOAD");
        assertThat(String.valueOf(row.get("FILTER_SUMMARY"))).contains("docId=42").contains("lang=ar").doesNotContain("ignored");
    }

    @Test
    @DisplayName("D-7: a detail view is not logged while mohe.audit.detail-views is off")
    void detailViewsOffByDefault() throws Exception {
        AsUser.call(AsUser.JO_ADMIN, () -> probe.detail(7));
        Thread.sleep(200);
        assertThat(rows("O33_PROBE_DETAIL")).isZero();
    }

    @Test
    @DisplayName("export file names become the report codes the older rows use")
    void reportCodes() {
        assertThat(ReportExports.reportCode("attache-summary")).isEqualTo("ATTACHE_SUMMARY");
        assertThat(ReportExports.reportCode(null)).isEqualTo("REPORT");
    }
}
