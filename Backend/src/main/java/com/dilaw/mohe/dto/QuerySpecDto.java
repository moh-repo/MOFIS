package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A query described structurally, never as SQL.
 *
 * <p>The client names a source, columns, filters and a sort order; the server
 * composes the statement. No field here carries SQL text, and none is ever
 * concatenated into a statement without first being matched against the schema
 * catalogue — identifiers are looked up, values are bound.
 *
 * <p>Accepting SQL from the client and sanitising it would be the other design.
 * It is not used, because the application connects to SQL Server with an account
 * that can write, so a gap in sanitising would be a gap that can change data.
 */
@Getter
@Setter
public class QuerySpecDto {

    /** Table or view in MOHECORE, as named by the catalogue. The root of the FROM clause. */
    private String source;

    /**
     * Further sources joined to {@link #source}, in the order they are joined.
     *
     * <p>Each carries the two columns to match on, so the server never has to
     * guess a relationship. {@code GET /joins/{table}} offers the ones the
     * schema declares as foreign keys.
     */
    private List<Join> joins = new ArrayList<>();

    /**
     * Columns to return. Empty means every column the catalogue lists, across
     * every source, up to the column cap.
     *
     * <p>A name may be bare ({@code STUDENT_NAME}) or qualified with its table
     * ({@code STUDENT_DATA.STUDENT_NAME}). Bare names are resolved against the
     * sources in the query and rejected as ambiguous if more than one has them,
     * so a joined query never silently returns a column from the wrong table.
     */
    private List<String> columns = new ArrayList<>();

    private List<Filter> filters = new ArrayList<>();

    private List<Sort> sorts = new ArrayList<>();

    /** Rows to return; clamped by the service to its maximum. */
    private Integer limit;

    /** Optional country narrowing, subject to the caller's own scope. */
    private Integer countryId;

    private List<Integer> countryIds = new ArrayList<>();

    /**
     * One join: which object to bring in, how, and on what.
     *
     * <p>{@code leftTable} names a source already in the query — the root or an
     * earlier join. Leaving it empty means the root, which is the common case.
     */
    @Getter
    @Setter
    public static class Join {
        /** Object to join in, as named by the catalogue. */
        private String table;
        /** INNER or LEFT; anything else is rejected. */
        private String type;
        /** Source already in the query that the ON clause matches against. */
        private String leftTable;
        /** Column on {@link #leftTable}. */
        private String leftColumn;
        /** Column on {@link #table}. */
        private String column;
    }

    @Getter
    @Setter
    public static class Filter {
        private String column;
        /** One of the operators the service recognises; anything else is rejected. */
        private String operator;
        private String value;
        /** Second operand, for BETWEEN. */
        private String value2;
        /** Operands for IN. */
        private List<String> values = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Sort {
        private String column;
        /** ASC or DESC; anything else is rejected. */
        private String direction;
    }
}
