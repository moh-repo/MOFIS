package com.dilaw.mohe.service;

import com.dilaw.mohe.model.QueryBuilder;
import com.dilaw.mohe.repository.QueryBuilderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The schema catalogue the query builder page lists, and which will decide what
 * a composed query is allowed to touch.
 */
@Transactional(readOnly = true)
@Service
public class QueryBuilderService {

    @Autowired
    private QueryBuilderRepository repository;

    public List<QueryBuilder> getAllQueryMetadata() {
        return repository.findCatalog();
    }

    public List<QueryBuilder> getColumnsByTableName(String tableName) {
        return repository.findCatalogForTable(tableName);
    }

    /**
     * Columns a query may filter or sort on. This was the entire schema while
     * the flag was a hardcoded 1; the ntext columns now excluded cannot appear
     * in a WHERE or ORDER BY without an explicit cast.
     */
    public List<QueryBuilder> getFilterableColumns() {
        return repository.findFilterableColumns();
    }

    /** The same types limit sorting and filtering, so the two lists agree. */
    public List<QueryBuilder> getSortableColumns() {
        return repository.findFilterableColumns();
    }

    public List<String> getAllTableNames() {
        return repository.findAllTableNames();
    }

    /**
     * Ways the given object can be joined to another, for the query builder's
     * join picker. Suggestions only — the engine validates the join it is
     * actually sent.
     */
    public List<com.dilaw.mohe.dto.JoinSuggestionDto> getJoinSuggestions(String tableName) {
        return repository.findJoinSuggestions(tableName);
    }
}

