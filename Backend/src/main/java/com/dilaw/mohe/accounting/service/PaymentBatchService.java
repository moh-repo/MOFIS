package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.dto.FinanceCandidate;
import com.dilaw.mohe.accounting.dto.PaymentBatchCreateRequest;
import com.dilaw.mohe.accounting.dto.PaymentBatchDetail;
import com.dilaw.mohe.accounting.dto.PaymentBatchLineView;
import com.dilaw.mohe.accounting.dto.PaymentBatchSummary;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.model.PaymentBatch;
import com.dilaw.mohe.accounting.model.PaymentBatchLine;
import com.dilaw.mohe.accounting.repository.PaymentBatchLineRepository;
import com.dilaw.mohe.accounting.repository.PaymentBatchRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payment batches — the دفعة, which the legacy schema called DOF.
 *
 * <p>A batch is the document the disbursement cycle hangs on: it is prepared
 * (which students, which amounts), approved, and then paid. The status flow is
 * what {@code CK_BATCH_STATUS} and {@code CK_BATCH_DISBURSE_ORDER} allow:
 *
 * <pre>
 *   OPEN --approve--> APPROVED --pay--> DISBURSED
 *    |
 *    +--cancel--> CANCELLED
 * </pre>
 *
 * <p>{@code CLOSED} exists in the constraint for a quarter-close step this
 * round does not build. Nothing here writes it.
 *
 * <p>Paying is the point the attaché's advance shrinks. {@link #markPaid} hands
 * the disbursed total to {@link AdvanceBalanceService#recordDisbursement}, and
 * the database refuses a batch the attaché cannot cover
 * ({@code CK_ADV_NOT_OVERDRAWN}) — surfaced as a 409, because the request was
 * coherent and the state simply does not allow it.
 *
 * <p>Maker and checker are two people: the officer who prepared the batch
 * cannot approve it, however many authorities they hold. Same rule as
 * {@link AllocationService#approve}, for the same reason.
 */
@Service
@Transactional(readOnly = true)
public class PaymentBatchService {

    @Autowired private PaymentBatchRepository batchRepository;
    @Autowired private PaymentBatchLineRepository lineRepository;
    @Autowired private FiscalPeriodService fiscalPeriodService;
    @Autowired private AdvanceBalanceService advanceBalanceService;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private AttacheCountryValidator attacheCountries;
    @Autowired private com.dilaw.mohe.security.policy.AccessControl accessControl;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ reads

    /**
     * The batches of a quarter the caller may see, with totals.
     *
     * @param countryId narrows to one attaché; null means every attaché in scope
     */
    public List<PaymentBatchSummary> forQuarter(Integer moheQuarterKey, Integer countryId) {
        if (moheQuarterKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quarterKey is required");
        }
        List<PaymentBatch> batches = countryId == null
                ? batchRepository.findByMoheQuarterKeyOrderByStudyCountryInIdAscBatchNoAsc(moheQuarterKey)
                : batchRepository.findByMoheQuarterKeyAndStudyCountryInIdOrderByBatchNoAsc(moheQuarterKey, countryId);
        return summarise(inScope(batches));
    }

    /** A batch and its lines. Not found rather than forbidden — see {@link #require}. */
    public PaymentBatchDetail detail(Integer id) {
        PaymentBatch batch = require(id);
        return toDetail(batch, lineRepository.findByBatchIdOrderByIdAsc(id));
    }

    /**
     * One batch by id, and the gate for every action on it.
     *
     * <p>Not found rather than forbidden: batch ids are sequential, and a 403
     * would tell an attaché how many batches every other attaché has raised.
     */
    public PaymentBatch require(Integer id) {
        PaymentBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment batch not found"));
        if (!countryScopeService.canAccessCountry(batch.getStudyCountryInId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment batch not found");
        }
        return batch;
    }

    /**
     * The quarter's finance rows for one attaché, offered as lines for a new
     * batch.
     *
     * <p>Read from {@code STUDENT_FINANCE} the way V045 read it: entitled is the
     * received amount, what actually goes out is the persisted {@code NET_AMOUNT}
     * (received minus Wafar). Students already carried in full by a non-cancelled
     * batch of the same quarter and attaché are flagged so the screen does not
     * offer to pay them twice; a held student is not flagged, because batch 2
     * exists precisely to pay whoever fell out of batch 1.
     */
    public List<FinanceCandidate> candidates(Integer moheQuarterKey, Integer countryId) {
        if (moheQuarterKey == null || countryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quarterKey and countryId are required");
        }
        if (!countryScopeService.canAccessCountry(countryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Reading another attaché's finance rows is not permitted.");
        }
        int year = moheQuarterKey / 10;
        int quarter = moheQuarterKey % 10;
        return jdbcTemplate.query("""
                SELECT f.STUDENT_ID, sd.STUDENT_NAME,
                       CAST(ISNULL(f.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS decimal(18,2)) AS entitled,
                       CAST(ISNULL(f.WAFAR_AMOUNT, 0) AS decimal(18,2))                AS wafar,
                       CAST(ISNULL(f.NET_AMOUNT, 0) AS decimal(18,2))                  AS net,
                       f.STUDY_CURRENCY_IN_ID,
                       CASE WHEN EXISTS (
                            SELECT 1 FROM FINCORE.PAYMENT_BATCH_LINE l
                            JOIN FINCORE.PAYMENT_BATCH b ON b.BATCH_ID = l.BATCH_ID
                            WHERE l.STUDENT_ID = f.STUDENT_ID
                              AND b.MOHE_QUARTER_KEY = ? AND b.STUDY_COUNTRY_IN_ID = ?
                              AND b.STATUS <> 'CANCELLED'
                              AND l.ENTITLED_AMOUNT > 0
                              AND l.DISBURSED_AMOUNT >= l.ENTITLED_AMOUNT)
                            THEN 1 ELSE 0 END AS already_paid
                FROM MOHECORE.STUDENT_FINANCE f
                LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = f.STUDENT_ID
                WHERE f.STUDENT_FIN_RECIEVED_YEAR = ? AND f.STUDENT_FIN_RECIEVED_MONTH = ?
                  AND f.STUDY_COUNTRY_IN_ID = ?
                  AND ISNULL(sd.IS_DELETED, 0) = 0
                ORDER BY f.STUDENT_ID
                """,
                (rs, i) -> new FinanceCandidate(
                        rs.getInt("STUDENT_ID"),
                        rs.getString("STUDENT_NAME"),
                        rs.getBigDecimal("entitled"),
                        rs.getBigDecimal("wafar"),
                        rs.getBigDecimal("net"),
                        (Integer) rs.getObject("STUDY_CURRENCY_IN_ID"),
                        rs.getInt("already_paid") == 1),
                moheQuarterKey, countryId, year, quarter, countryId);
    }

    // ----------------------------------------------------------------- writes

    /**
     * Prepares a batch: the next DOF number for the quarter and attaché, in
     * {@code OPEN}, with the signed-in user as preparer.
     *
     * <p>The preparer is whoever is signed in, never a field of the request:
     * {@link #approve} compares against it.
     */
    @Transactional
    public PaymentBatchDetail create(PaymentBatchCreateRequest request) {
        if (request == null || request.moheQuarterKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "moheQuarterKey is required");
        }
        if (request.currencyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyId is required");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A batch must carry at least one line. An empty document pays nobody.");
        }
        Integer countryId = resolveCountryForWrite(request.studyCountryInId());
        attacheCountries.requireActive(countryId);
        fiscalPeriodService.requireMoheQuarter(request.moheQuarterKey()); // R5-20
        FiscalYear year = fiscalPeriodService.requireYearOfQuarter(request.moheQuarterKey());
        fiscalPeriodService.assertYearOpen(year.getId()); // FIN-12

        List<PaymentBatchLine> lines = validateLines(request.lines(), countryId);
        requireStudents(lines);
        requireStudentsOfAttache(lines, countryId);
        requireNotAlreadyPaid(lines, request.moheQuarterKey());

        PaymentBatch batch = new PaymentBatch();
        batch.setFiscalYearId(year.getId());
        batch.setMoheQuarterKey(request.moheQuarterKey());
        batch.setStudyCountryInId(countryId);
        Short highest = batchRepository.highestBatchNo(request.moheQuarterKey(), countryId);
        batch.setBatchNo((short) ((highest == null ? 0 : highest) + 1));
        batch.setFeeTypeId(request.feeTypeId());
        batch.setCurrencyId(request.currencyId());
        batch.setStatus(PaymentBatch.OPEN);
        batch.setNotes(request.notes());
        batch.setPreparedAt(Instant.now());
        countryScopeService.getCurrentUserId().ifPresent(uid -> batch.setPreparedBy(uid.intValue()));
        PaymentBatch saved = batchRepository.save(batch);

        lines.forEach(l -> l.setBatchId(saved.getId()));
        return toDetail(saved, lineRepository.saveAll(lines));
    }

    /**
     * Approves an open batch.
     *
     * <p>The preparer cannot approve their own batch, and there is no exemption
     * for administrators — the rule exists precisely for the accounts that can
     * do everything else.
     */
    @Transactional
    public PaymentBatch approve(Integer id, Integer userId) {
        PaymentBatch batch = require(id);
        if (!PaymentBatch.OPEN.equals(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an open batch can be approved; this one is " + batch.getStatus() + ".");
        }
        // R-16: MakerCheckerPolicy — the signed-in checker is not the preparer; no exemptions.
        accessControl.assertAllowed(com.dilaw.mohe.security.policy.MakerChecker.paymentBatch(batch.getPreparedBy(), userId), "APPROVE");
        fiscalPeriodService.assertYearOpen(batch.getFiscalYearId()); // FIN-12
        batch.setStatus(PaymentBatch.APPROVED);
        batch.setApprovedBy(userId);
        batch.setApprovedAt(Instant.now());
        return batchRepository.save(batch);
    }

    /**
     * Records that the batch has been paid out.
     *
     * <p>This is where the advance moves. The disbursed total of the lines is
     * booked against the attaché's balance for the batch's year and currency; if
     * the attaché does not hold that much un-earmarked cash the database refuses
     * and the batch stays {@code APPROVED}.
     *
     * <p>FIN-03: two "mark paid" clicks at once both passed the {@code APPROVED} check
     * and the advance was debited twice. The status change is now written and flushed
     * first, under the batch's row version, so the second request fails with a version
     * conflict (409) before it reaches the advance, and rolls back whole. FIN-12: the
     * batch's year and today's period must be open.
     */
    @Transactional
    public PaymentBatch markPaid(Integer id, Integer userId) {
        PaymentBatch batch = require(id);
        if (!PaymentBatch.APPROVED.equals(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A batch must be approved before it is paid; this one is " + batch.getStatus() + ".");
        }
        fiscalPeriodService.assertYearOpen(batch.getFiscalYearId());
        fiscalPeriodService.assertPeriodOpen(java.time.LocalDate.now());
        BigDecimal disbursed = lineRepository.findByBatchIdOrderByIdAsc(id).stream()
                .map(PaymentBatchLine::getDisbursedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        batch.setStatus(PaymentBatch.DISBURSED);
        batch.setDisbursedAt(Instant.now());
        PaymentBatch saved = batchRepository.saveAndFlush(batch);

        if (disbursed.signum() > 0) {
            // A balance row is opened at zero if the attaché has none for this year
            // and currency yet, so that the refusal is "this would overdraw the
            // advance" rather than "no advance balance" — the first is the truth.
            advanceBalanceService.openBalance(saved.getFiscalYearId(), saved.getStudyCountryInId(), saved.getCurrencyId());
            advanceBalanceService.recordDisbursement(
                    saved.getFiscalYearId(), saved.getStudyCountryInId(), saved.getCurrencyId(), disbursed);
        }
        return saved;
    }

    /**
     * Withdraws an open batch. Nothing has moved, so nothing is reversed.
     *
     * <p>FIN-16: recorded as cancelled by and at. It used to write APPROVED_BY and
     * APPROVED_AT, so a cancelled batch read as approved by whoever withdrew it.
     */
    @Transactional
    public PaymentBatch cancel(Integer id, Integer userId) {
        PaymentBatch batch = require(id);
        if (!PaymentBatch.OPEN.equals(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an open batch can be cancelled; this one is " + batch.getStatus() + ".");
        }
        batch.setStatus(PaymentBatch.CANCELLED);
        batch.setCancelledBy(userId);
        batch.setCancelledAt(Instant.now());
        return batchRepository.save(batch);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The attaché a new batch belongs to. The column is NOT NULL — a batch is
     * always one attaché's document — so the ministry has to say which; an
     * officer holding a single attaché gets theirs whether or not they asked.
     */
    private Integer resolveCountryForWrite(Integer requested) {
        CountryScope scope = countryScopeService.getScope();
        if (requested == null) {
            if (!scope.isMinistryUser() && scope.getAllowedCountryIds().size() == 1) {
                return scope.getAllowedCountryIds().get(0);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attaché paying this batch is required (studyCountryInId).");
        }
        // Forbidden, not 404: the caller named the attaché themselves, so there is
        // nothing to conceal, and a 404 on a create would read as a failed save.
        if (!countryScopeService.canAccessCountry(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Preparing a batch for an attaché outside your scope is not permitted.");
        }
        return requested;
    }

    private static List<PaymentBatchLine> validateLines(List<PaymentBatchCreateRequest.Line> requested,
                                                        Integer countryId) {
        Set<Integer> seen = new HashSet<>();
        List<PaymentBatchLine> lines = new ArrayList<>();
        for (PaymentBatchCreateRequest.Line in : requested) {
            if (in == null || in.studentId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every line needs a studentId.");
            }
            if (!seen.add(in.studentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Student " + in.studentId() + " appears twice; a batch carries one line per student.");
            }
            BigDecimal entitled = in.entitledAmount();
            if (entitled == null || entitled.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Student " + in.studentId() + ": the entitled amount cannot be negative.");
            }
            BigDecimal disbursed = in.disbursedAmount() == null ? entitled : in.disbursedAmount();
            if (disbursed.signum() < 0 || disbursed.compareTo(entitled) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Student " + in.studentId() + ": a line cannot disburse more than it entitles.");
            }
            PaymentBatchLine line = new PaymentBatchLine();
            line.setStudentId(in.studentId());
            line.setEntitledAmount(entitled);
            line.setDisbursedAmount(disbursed);
            line.setHeldReason(in.heldReason());
            line.setStudyCountryInId(countryId);
            lines.add(line);
        }
        return lines;
    }

    /**
     * Every line names a student the caller can see (round-5 QA R5-20): a line for a
     * student that does not exist reached the foreign key, and the answer was the delete
     * message, "still used by other records". Read under the caller's row-level scope, so
     * a student of another attaché is refused the same way, as elsewhere (N14); a deleted
     * student is not paid either (H7).
     */
    private void requireStudents(List<PaymentBatchLine> lines) {
        List<Integer> ids = lines.stream().map(PaymentBatchLine::getStudentId).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Set<Integer> found = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT sd.STUDENT_ID FROM MOHECORE.STUDENT_DATA sd WHERE sd.STUDENT_ID IN (" + placeholders + ") AND "
                        + com.dilaw.mohe.util.LiveStudentSql.SD_NOT_DELETED,
                Integer.class, ids.toArray()));
        List<Integer> missing = ids.stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No student " + missing.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                            + " can be paid from this batch: the student does not exist, was deleted, or is not in your scope.");
        }
    }

    /**
     * FIN-04 (b): every line's student studies at the batch's attaché. A batch is paid
     * from one attaché's advance, and a Riyadh student on a Cairo batch was paid out of
     * Cairo's. A student with no attaché on record is refused too: there is no advance
     * it could rightly come from. 400 naming the students.
     */
    private void requireStudentsOfAttache(List<PaymentBatchLine> lines, Integer countryId) {
        List<Integer> ids = lines.stream().map(PaymentBatchLine::getStudentId).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids);
        args.add(countryId);
        List<Integer> elsewhere = jdbcTemplate.queryForList(
                "SELECT sd.STUDENT_ID FROM MOHECORE.STUDENT_DATA sd WHERE sd.STUDENT_ID IN (" + placeholders + ")"
                        + " AND " + com.dilaw.mohe.util.LiveStudentSql.SD_NOT_DELETED
                        + " AND ISNULL(sd.STUDY_COUNTRY_IN, -1) <> ?",
                Integer.class, args.toArray());
        if (!elsewhere.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Student " + elsewhere.stream().sorted().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", "))
                            + " does not study at this batch's attaché, so cannot be paid from its advance.");
        }
    }

    /**
     * FIN-04 (a): a student already paid in full by another non-cancelled batch of the
     * same quarter is not paid again. {@link #candidates} flags such students for the
     * screen, but nothing on the server refused them. Any attaché's batch counts: one
     * quarter's entitlement is paid once. A held student (disbursed less than entitled)
     * is not refused, because batch 2 exists to pay whoever batch 1 held back. 409,
     * naming the students.
     */
    private void requireNotAlreadyPaid(List<PaymentBatchLine> lines, Integer moheQuarterKey) {
        List<Integer> ids = lines.stream().map(PaymentBatchLine::getStudentId).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(moheQuarterKey);
        args.addAll(ids);
        List<Integer> paid = jdbcTemplate.queryForList(
                "SELECT DISTINCT l.STUDENT_ID FROM FINCORE.PAYMENT_BATCH_LINE l"
                        + " JOIN FINCORE.PAYMENT_BATCH b ON b.BATCH_ID = l.BATCH_ID"
                        + " WHERE b.MOHE_QUARTER_KEY = ? AND b.STATUS <> 'CANCELLED'"
                        + " AND l.ENTITLED_AMOUNT > 0 AND l.DISBURSED_AMOUNT >= l.ENTITLED_AMOUNT"
                        + " AND l.STUDENT_ID IN (" + placeholders + ")",
                Integer.class, args.toArray());
        if (!paid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Student " + paid.stream().sorted().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", "))
                            + " is already paid in full by another batch of this quarter.");
        }
    }

    private PaymentBatchDetail toDetail(PaymentBatch batch, List<PaymentBatchLine> lines) {
        Map<Integer, String> names = studentNames(lines.stream().map(PaymentBatchLine::getStudentId).toList());
        BigDecimal entitled = lines.stream().map(PaymentBatchLine::getEntitledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disbursed = lines.stream().map(PaymentBatchLine::getDisbursedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentBatchLineView> views = lines.stream()
                .map(l -> new PaymentBatchLineView(l.getId(), l.getStudentId(), names.get(l.getStudentId()),
                        l.getEntitledAmount(), l.getDisbursedAmount(), l.getHeldReason()))
                .toList();
        return new PaymentBatchDetail(PaymentBatchSummary.of(batch, lines.size(), entitled, disbursed), views);
    }

    private List<PaymentBatchSummary> summarise(List<PaymentBatch> batches) {
        if (batches.isEmpty()) return List.of();
        Map<Integer, Object[]> totals = new HashMap<>();
        for (Object[] row : lineRepository.totalsByBatch(batches.stream().map(PaymentBatch::getId).toList())) {
            totals.put((Integer) row[0], row);
        }
        return batches.stream().map(b -> {
            Object[] t = totals.get(b.getId());
            return t == null
                    ? PaymentBatchSummary.of(b, 0, BigDecimal.ZERO, BigDecimal.ZERO)
                    : PaymentBatchSummary.of(b, ((Number) t[1]).longValue(), (BigDecimal) t[2], (BigDecimal) t[3]);
        }).toList();
    }

    private List<PaymentBatch> inScope(List<PaymentBatch> all) {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return all;
        List<Integer> allowed = scope.getAllowedCountryIds();
        return all.stream().filter(b -> allowed.contains(b.getStudyCountryInId())).toList();
    }

    /** Names for the detail dialog, read in one round trip. Read-only on STUDENT_DATA. */
    private Map<Integer, String> studentNames(List<Integer> studentIds) {
        Map<Integer, String> names = new HashMap<>();
        if (studentIds.isEmpty()) return names;
        String placeholders = String.join(",", Collections.nCopies(studentIds.size(), "?"));
        jdbcTemplate.query(
                "SELECT STUDENT_ID, STUDENT_NAME FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID IN (" + placeholders + ")",
                rs -> { names.put(rs.getInt(1), rs.getString(2)); },
                studentIds.toArray());
        return names;
    }
}
