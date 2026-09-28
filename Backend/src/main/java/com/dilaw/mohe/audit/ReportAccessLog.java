package com.dilaw.mohe.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Tracks who exported which report, when, and with what filters.
 * Written asynchronously — never blocks the HTTP response.
 *
 * <p>{@link #studyCountryInId} is the row's tenant. The table is under the V031
 * row-level security policy on that column, but the entity never mapped it, so
 * every row was written NULL and the predicate matched nothing it was meant to
 * separate. It is stamped from the exporting user's scope by
 * {@link ReportAccessLogService}; V065 backfills history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "REPORT_ACCESS_LOG", schema = "MOHECORE")
public class ReportAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID")
    private Long logId;

    @Column(name = "REPORT_CODE", nullable = false, length = 100)
    private String reportCode;

    @Column(name = "EXPORT_FORMAT", length = 20)
    private String exportFormat;

    @Column(name = "USERNAME", length = 150)
    private String username;

    @Nationalized
    @Column(name = "FILTER_SUMMARY", length = 1000)
    private String filterSummary;

    @Column(name = "ACCESSED_AT", nullable = false)
    private Instant accessedAt;

    @Column(name = "IP_ADDRESS", length = 50)
    private String ipAddress;

    /** The exporting user's single study country; NULL for ministry-wide or multi-country users (global). */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    /**
     * Rows in the export. Already counted for {@link #filterSummary}; recorded as
     * a number so the Export Manager can show it without parsing prose.
     */
    @Column(name = "ROW_COUNT")
    private Integer rowCount;

    /** Size of the generated file. NULL on a row written before the file existed. */
    @Column(name = "SIZE_BYTES")
    private Long sizeBytes;

    public ReportAccessLog(String reportCode, String exportFormat, String username,
                           String filterSummary, String ipAddress) {
        this.reportCode    = reportCode;
        this.exportFormat  = exportFormat;
        this.username      = username;
        this.filterSummary = filterSummary;
        this.ipAddress     = ipAddress;
        this.accessedAt    = Instant.now();
    }
}
