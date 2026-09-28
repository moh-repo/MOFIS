package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/** Rows a composed query returned, with the statement that produced them. */
@Getter
@Setter
public class QueryResultDto {

    /** Columns in the order they were selected, with the type from the catalogue. */
    private List<Column> columns;

    private List<Map<String, Object>> rows;

    private int rowCount;

    /** True when the row cap was reached, so the result is a prefix of the answer. */
    private boolean truncated;

    /**
     * The statement that ran, with parameter placeholders left in.
     *
     * <p>Returned so the query is inspectable rather than something the page does
     * on the user's behalf without showing it. Values stay in {@link #parameters}
     * and are never spliced into this string.
     */
    private String sql;

    private List<Object> parameters;

    /** Set when country scope narrowed the query, so the reader knows it did. */
    private String scopeNote;

    @Getter
    @Setter
    public static class Column {
        private String name;
        private String dataType;

        public Column(String name, String dataType) {
            this.name = name;
            this.dataType = dataType;
        }
    }
}
