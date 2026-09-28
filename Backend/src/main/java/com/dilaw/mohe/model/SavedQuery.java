package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A saved query builder query, held as its structured spec rather than as SQL.
 *
 * <p>{@link #specJson} is a serialised {@code QuerySpecDto}. Storing SQL would
 * mean a statement written under one set of rules running later under another;
 * a spec is re-validated against the catalogue and the deny list every time it
 * runs, and the country scope applied is the scope of whoever runs it.
 */
@Getter
@Setter
@Entity
@Table(name = "SAVED_QUERY", schema = "MOHECORE")
public class SavedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SAVED_QUERY_ID")
    private Long savedQueryId;

    @Column(name = "QUERY_NAME", nullable = false, length = 200)
    private String queryName;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    /** Denormalised from the spec so the list reads without parsing JSON. */
    @Column(name = "SOURCE_NAME", nullable = false, length = 128)
    private String sourceName;

    @Column(name = "SPEC_JSON", nullable = false)
    private String specJson;

    @Column(name = "OWNER_USERNAME", nullable = false, length = 200)
    private String ownerUsername;

    @Column(name = "IS_SHARED", nullable = false)
    private Boolean shared = Boolean.FALSE;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * How many times this query has been run, counted by
     * {@code SavedQueryService.run}. Queries saved before V106 start at 0: that
     * is "nothing was counted", not "never used", and the two are indistinguishable
     * from here.
     */
    @Column(name = "USAGE_COUNT", nullable = false)
    private Integer usageCount = 0;

    /** Rows the last run returned. NULL until it has been run once. */
    @Column(name = "LAST_ROW_COUNT")
    private Integer lastRowCount;

    /** When it was last run — as opposed to {@link #updatedAt}, when it was last edited. */
    @Column(name = "LAST_RUN_AT")
    private LocalDateTime lastRunAt;
}
