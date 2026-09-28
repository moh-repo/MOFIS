package com.dilaw.mohe.dto;

/**
 * A way two catalogued objects can be joined.
 *
 * <p>Offered to the query builder so a user does not have to know the schema's
 * keys. It is a suggestion and nothing more — {@code QueryExecutionService}
 * validates whatever the client sends back against the catalogue regardless, so
 * a stale or wrong suggestion produces a refused query, never a bad statement.
 */
public interface JoinSuggestionDto {

    /** The object the suggestion was asked for. */
    String getFromTable();

    String getFromColumn();

    /** The object that could be joined to it. */
    String getToTable();

    String getToColumn();

    /**
     * {@code FK} when the database declares this relationship, {@code NAME} when
     * it is inferred from two columns sharing a key-shaped name.
     *
     * <p>Both are offered because the 39 reporting views carry no foreign keys —
     * they are the objects most worth joining and the ones the database can say
     * least about.
     */
    String getKind();
}
