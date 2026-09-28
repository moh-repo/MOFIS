package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.service.QuarterFinanceTotalsService.QuarterTotals;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class MoheQuarterService {

    @Autowired private MoheQuarterRepository repository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private QuarterFinanceTotalsService financeTotalsService;

    @PersistenceContext private EntityManager entityManager;

    /** Returns all quarters the current user is allowed to see. */
    public List<MoheQuarter> getAll() {
        CountryScope scope = countryScopeService.getScope();
        List<MoheQuarter> quarters;
        if (scope.isMinistryUser()) {
            quarters = repository.findAll();
        } else {
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return List.of();
            quarters = repository.findByCountryIdInOrCountryIdIsNull(allowed);
        }
        return withFinanceTotals(quarters, scope);
    }

    /** Most recent open quarter visible to the current user. */
    public Optional<MoheQuarter> getCurrent() {
        CountryScope scope = countryScopeService.getScope();
        Optional<MoheQuarter> current;
        if (scope.isMinistryUser()) {
            current = repository.findTopByQuarterClosedFalseOrderByQuarterYearDescQuarterNumberDesc();
        } else {
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return Optional.empty();
            List<MoheQuarter> open = repository.findOpenInScope(allowed);
            current = open.isEmpty() ? Optional.empty() : Optional.of(open.get(0));
        }
        current.ifPresent(q -> withFinanceTotals(List.of(q), scope));
        return current;
    }

    public MoheQuarter getById(int id) {
        MoheQuarter q = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheQuarter not found"));
        assertAccess(q);
        return withFinanceTotals(List.of(q), countryScopeService.getScope()).get(0);
    }

    /**
     * Fills each quarter's amount from the student finance rows behind it.
     *
     * <p>The quarters are <b>detached first</b>. {@code quarterAmount} maps a real
     * column, so writing a derived figure into a managed entity would make a GET
     * capable of updating the database on flush. A read must not write, and
     * detaching says so rather than relying on the read-only transaction's flush
     * mode to say it quietly.
     */
    private List<MoheQuarter> withFinanceTotals(List<MoheQuarter> quarters, CountryScope scope) {
        if (quarters.isEmpty()) return quarters;
        Map<Integer, QuarterTotals> totals = financeTotalsService.totalsByQuarter(scope);
        Map<Integer, java.math.BigDecimal> budgets = financeTotalsService.budgetByQuarter(scope);
        for (MoheQuarter q : quarters) {
            entityManager.detach(q);
            // BUDGET_AMOUNT on the row is the ministry-wide total. Replace it with
            // what reached the caller's own attachés, or a country officer reads
            // every other attaché's budget off this screen.
            java.math.BigDecimal budget = budgets.get(quarterKeyOf(q));
            q.setBudgetAmount(budget == null ? 0L : budget.longValue());
            QuarterTotals t = totals.get(quarterKeyOf(q));
            if (t == null) {
                // No finance rows for this quarter — a quarter that has been created
                // but not yet paid. Zero, not the stale hand-entered column value.
                q.setQuarterAmount(0);
                q.setQuarterWafarAmount(0L);
                q.setQuarterNetAmount(0L);
                q.setQuarterStudentCount(0);
                q.setQuarterAmountSource(null);
            } else {
                q.setQuarterAmount(Math.toIntExact(t.received()));
                q.setQuarterWafarAmount(t.wafar());
                q.setQuarterNetAmount(t.net());
                q.setQuarterStudentCount(t.studentCount());
                q.setQuarterAmountSource(t.source().name());
            }
        }
        return quarters;
    }

    /**
     * The {@code YYYYQ} key for a quarter row, from the year and the quarter number.
     *
     * <p>{@code MOHE_QUARTER.QUARTER} holds the same value — it is the table's primary
     * key and the target of five foreign keys, {@code STUDENT_FINANCE.MOHE_QUARTER_ID}
     * among them — and is now derived from these two on every write. This computes it
     * rather than reading the column so that a quarter still being edited in memory
     * gives the right answer.
     */
    private static Integer quarterKeyOf(MoheQuarter q) {
        if (q.getQuarterYear() == null || q.getQuarterNumber() == null) return q.getId();
        return QuarterFinanceTotalsService.quarterKey(q.getQuarterYear(), q.getQuarterNumber());
    }

    @Transactional
    public MoheQuarter update(Integer id, MoheQuarter entity) {
        MoheQuarter existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheQuarter not found"));
        assertAccess(existing);
        validateQuarterNumber(entity);
        requirePeriod(entity);
        existing.setQuarterName(entity.getQuarterName());
        existing.setQuarterYear(entity.getQuarterYear());
        existing.setQuarterNumber(entity.getQuarterNumber());
        // QUARTER follows the period rather than the body. It used to be copied from
        // entity.getQuarter(), and the screen has never sent one — so an edit wrote NULL
        // into a NOT NULL column, and an edit that moved the year or the number would
        // otherwise have left the period key behind.
        existing.setQuarter(QuarterFinanceTotalsService.quarterKey(
                entity.getQuarterYear(), entity.getQuarterNumber()));
        // quarterAmount is deliberately not copied. It is summed from the student
        // finance tables on every read, so accepting it here would let a save round
        // the derived figure back into the column — and a later change to a finance
        // row would then silently disagree with what the screen shows.
        existing.setQuarterPayDate(entity.getQuarterPayDate());
        existing.setQuarterRecivedDate(entity.getQuarterRecivedDate());
        existing.setNotes(entity.getNotes());
        // budgetAmount is not copied either, and for a sharper reason than
        // quarterAmount: the column holds the ministry-wide total from
        // AllocationService.rollUpToQuarter, while a country officer is now shown
        // their own attachés' share. Saving what they were shown would overwrite
        // the ministry's figure with one country's.
        // countryId is intentionally not editable after creation.
        existing.setQuarterClosed(existing.getQuarterClosed());
        return repository.save(existing);
    }

    @Transactional
    public void delete(Integer id) {
        MoheQuarter q = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheQuarter not found"));
        assertAccess(q);
        repository.deleteById(id);
    }

    /** Enforces that the current user's country scope includes this quarter. */
    private void assertAccess(MoheQuarter q) {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return;
        // Global quarters (countryId == null) are visible to everyone.
        if (q.getCountryId() == null) return;
        if (!scope.getAllowedCountryIds().contains(q.getCountryId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied to quarter for country " + q.getCountryId());
        }
    }

    private static void validateQuarterNumber(MoheQuarter entity) {
        if (entity.getQuarterNumber() == null) return;
        int n = entity.getQuarterNumber();
        if (n < 1 || n > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quarter number must be between 1 and 4");
        }
    }

    /** All three of QUARTER, QUARTER_YEAR and QUARTER_NO are NOT NULL. */
    private static void requirePeriod(MoheQuarter entity) {
        if (entity.getQuarterYear() == null || entity.getQuarterNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quarterYear and quarterNumber are required: they are the quarter's period");
        }
    }
}
