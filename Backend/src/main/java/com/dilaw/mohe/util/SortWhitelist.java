package com.dilaw.mohe.util;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns {@code sortBy} / {@code sortDir} request parameters into a {@link Sort},
 * refusing any property that is not on the caller's allow-list.
 *
 * <p>Eleven list endpoints fed {@code Sort.by(sortBy)} straight from the query
 * string (plan item 2.4). Spring Data resolves the property against the entity
 * at query time, so an unknown name became a {@code PropertyReferenceException}
 * and a 500 — and, more to the point, the client could order by any mapped
 * column, including ones the screen never shows. Each controller now names the
 * properties it is willing to sort on, and anything else is a 400 that says so.
 *
 * <p>Deliberately not a silent fallback to a default column, which is what
 * {@code LookupTablePageUtil.toSort} and {@code UmsPageableUtil.of} do: a typo
 * in a sort parameter that quietly reorders the page is the kind of bug that is
 * found by a user, not by a test.
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    /**
     * @param sortBy   the requested property; null or blank falls back to the first
     *                 entry of {@code allowed}, which is why callers should pass an
     *                 ordered set when the default matters, or supply it via
     *                 {@code @RequestParam(defaultValue = ...)}
     * @param sortDir  {@code asc} or {@code desc} (case-insensitive); anything
     *                 else is descending, matching what every caller did before
     * @param allowed  the entity property names the endpoint may order by
     */
    public static Sort sort(String sortBy, String sortDir, Set<String> allowed) {
        return Sort.by(order(sortBy, sortDir, allowed));
    }

    /** As {@link #sort} but returns the single {@link Sort.Order} for callers that combine orders. */
    public static Sort.Order order(String sortBy, String sortDir, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            throw new IllegalArgumentException("A sort allow-list must name at least one property");
        }
        String property = (sortBy == null || sortBy.isBlank()) ? allowed.iterator().next() : sortBy.trim();
        if (!allowed.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot sort by '" + property + "'. Sortable fields: "
                            + allowed.stream().sorted().collect(Collectors.joining(", ")));
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir == null ? "" : sortDir.trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return new Sort.Order(direction, property);
    }
}
