package com.dilaw.mohe.service;

import com.dilaw.mohe.util.SecurityContextHelper;
import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.model.SavedQuery;
import com.dilaw.mohe.repository.SavedQueryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saving, listing and running query builder queries.
 *
 * <p>A saved query is a spec, not a statement. {@link #run} deserialises it and
 * hands it to {@link QueryExecutionService}, which validates it exactly as it
 * validates a query typed today — so a query saved before a source was denied
 * stops working when the source is denied, rather than continuing to run.
 */
@Service
public class SavedQueryService {

    private static final Logger log = LoggerFactory.getLogger(SavedQueryService.class);

    private final SavedQueryRepository repository;
    private final QueryExecutionService queryExecutionService;
    private final ObjectMapper objectMapper;

    public SavedQueryService(SavedQueryRepository repository,
                             QueryExecutionService queryExecutionService,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.queryExecutionService = queryExecutionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Saved queries visible to the caller, or none when the table does not exist.
     *
     * <p>MOHECORE.SAVED_QUERY is created by V026, which is applied by hand. Until
     * then this listing is the one call the page makes on load, and letting it
     * fail put a server-error toast on the screen every time the query builder
     * was opened — for a feature that is simply not installed yet. An empty list
     * says the same thing without the alarm.
     */
    @Transactional(readOnly = true)
    public List<SavedQuery> list() {
        try {
            return repository.findVisibleTo(SecurityContextHelper.currentUsername());
        } catch (DataAccessException ex) {
            log.warn("Saved queries are unavailable; MOHECORE.SAVED_QUERY may not exist yet "
                    + "(apply V026). {}", ex.getMostSpecificCause().getMessage());
            return List.of();
        }
    }

    @Transactional
    public SavedQuery save(String name, String description, boolean shared, QuerySpecDto spec) {
        if (name == null || name.trim().isEmpty()) {
            throw badRequest("A name is required");
        }
        if (spec == null || spec.getSource() == null || spec.getSource().trim().isEmpty()) {
            throw badRequest("A query with no source cannot be saved");
        }

        // Run the spec through validation before storing it, so an unusable query
        // is refused at save time rather than discovered on some later run.
        queryExecutionService.execute(spec);

        String username = SecurityContextHelper.currentUsername();
        SavedQuery saved = repository.findByOwnerUsernameAndQueryName(username, name.trim())
                .orElseGet(SavedQuery::new);

        boolean isNew = saved.getSavedQueryId() == null;
        saved.setQueryName(name.trim());
        saved.setDescription(description);
        saved.setSourceName(spec.getSource().trim());
        saved.setSpecJson(serialise(spec));
        saved.setOwnerUsername(username);
        saved.setShared(shared);
        if (isNew) {
            saved.setCreatedAt(LocalDateTime.now());
        } else {
            saved.setUpdatedAt(LocalDateTime.now());
        }
        return repository.save(saved);
    }

    /**
     * Runs a saved query and records that it was run.
     *
     * <p>Not {@code readOnly}: the Report Templates screen shows a usage count, a
     * row count and a last-used date for every saved query, and until V106 all
     * three were columns the view selected as literal NULL. This is the only
     * place that knows any of them.
     */
    @Transactional
    public QueryResultDto run(Long id, Integer countryId, List<Integer> countryIds) {
        SavedQuery saved = requireVisible(id);
        QuerySpecDto spec = deserialise(saved.getSpecJson());

        // The stored country selection is discarded. It belongs to whoever saved
        // the query; the scope that applies is the scope of whoever runs it, and
        // a shared query must not carry another user's countries with it.
        spec.setCountryId(countryId);
        spec.setCountryIds(countryIds == null ? List.of() : countryIds);

        QueryResultDto result = queryExecutionService.execute(spec);

        // Recorded after the query succeeds: a run that threw is not a use, and
        // counting it would make the figure a measure of attempts rather than of
        // results. The row count is what this run returned -- which is a prefix
        // of the answer when the cap was hit, and the screen labels it as an
        // estimate for exactly that reason.
        saved.setUsageCount(saved.getUsageCount() == null ? 1 : saved.getUsageCount() + 1);
        saved.setLastRowCount(result.getRowCount());
        saved.setLastRunAt(LocalDateTime.now());
        repository.save(saved);

        return result;
    }

    @Transactional(readOnly = true)
    public QuerySpecDto getSpec(Long id) {
        return deserialise(requireVisible(id).getSpecJson());
    }

    @Transactional
    public void delete(Long id) {
        SavedQuery saved = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such saved query"));
        // Sharing makes a query visible, not editable.
        if (!saved.getOwnerUsername().equals(SecurityContextHelper.currentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the owner may delete a saved query");
        }
        repository.delete(saved);
    }

    private SavedQuery requireVisible(Long id) {
        SavedQuery saved = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such saved query"));
        String username = SecurityContextHelper.currentUsername();
        if (!Boolean.TRUE.equals(saved.getShared()) && !saved.getOwnerUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This query is not shared with you");
        }
        return saved;
    }

    private String serialise(QuerySpecDto spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (JsonProcessingException ex) {
            throw badRequest("The query could not be stored: " + ex.getOriginalMessage());
        }
    }

    private QuerySpecDto deserialise(String json) {
        try {
            return objectMapper.readValue(json, QuerySpecDto.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The stored query could not be read");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
