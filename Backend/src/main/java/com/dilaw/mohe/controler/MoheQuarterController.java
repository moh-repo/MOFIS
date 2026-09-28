package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.service.MoheQuarterCloseService;
import com.dilaw.mohe.service.MoheQuarterCreateService;
import com.dilaw.mohe.service.MoheQuarterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/moheQuarter")
@PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER)
public class MoheQuarterController {

    @Autowired private MoheQuarterService service;
    @Autowired private MoheQuarterCloseService closeService;
    @Autowired private MoheQuarterCreateService createService;
    @Autowired private com.dilaw.mohe.service.MoheQuarterCreateJobs createJobs;

    /**
     * All quarters visible to the current user (filtered by country scope).
     *
     * <p>Also read by the finance screens, which name a quarter on records they
     * own — the books allowance form among them. The class gate is
     * {@code MOHE_QUARTER}, which none of the three finance roles hold, so the
     * quarter dropdown answered 403 and the list printed raw quarter ids. Finance
     * is added here rather than opening this under the reference-data gate: a
     * quarter carries {@code BUDGET_AMOUNT} and {@code PAID_AMOUNT}, which are
     * not a catalogue. Every other method keeps the gate it had.
     */
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER + " or " + MoheSecurityExpressions.FINANCE)
    @GetMapping("/")
    public List<MoheQuarter> getAll() {
        return service.getAll();
    }

    /** Most recent open quarter in the current user's scope. */
    @GetMapping("/current")
    public ResponseEntity<MoheQuarter> getCurrent() {
        return service.getCurrent()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * The defaults the Add dialog opens with: the quarter that follows the latest one on
     * record, with the name the existing rows are named by ("الربع الرابع 2025").
     *
     * <p>Nothing is reserved and nothing is written — the caller may change any of it,
     * and a later quarter is still accepted, so a skipped period can be recorded. Gated
     * with the create it feeds; there is no reason for anyone else to ask.
     */
    @GetMapping("/next")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_ADD)
    public ResponseEntity<Map<String, Object>> next() {
        return ResponseEntity.ok(createService.nextQuarterDefaults());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MoheQuarter> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /**
     * Starts creating a new quarter, which in one transaction:
     *  - archives current StudentDatum + StudentFinance snapshots (scoped by country)
     *  - advances StudentFinance year/quarter fields to the new quarter
     *  - advances StudentDatum.studentLastQuarter
     *
     * <p>It runs as a background job (review Q4): the request validates, answers 202
     * with the job, and the screen polls {@code /create-jobs/{jobId}} for progress and
     * the summary. An invalid period or out-of-scope country is still refused here.
     */
    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_ADD)
    public ResponseEntity<Map<String, Object>> create(@RequestBody MoheQuarter entity) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                .body(createJobs.start(entity, com.dilaw.mohe.config.SecurityRequestUtils.requireUserId()));
    }

    /** Progress of a quarter creation, and its summary once done. */
    @GetMapping("/create-jobs/{jobId}")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_ADD)
    public ResponseEntity<Map<String, Object>> createJob(@PathVariable String jobId) {
        return ResponseEntity.ok(createJobs.status(jobId));
    }

    /** The creation currently running, if any (204 when idle), for a screen opened mid-run. */
    @GetMapping("/create-jobs/current")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_ADD)
    public ResponseEntity<Map<String, Object>> currentCreateJob() {
        Map<String, Object> current = createJobs.current();
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }

    /** Update quarter metadata (name, dates, amounts). countryId is immutable after creation. */
    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_EDIT)
    public ResponseEntity<MoheQuarter> update(@PathVariable Integer id, @RequestBody MoheQuarter entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    /** Close the quarter: run the archive job and mark it closed. */
    @PostMapping("/{id}/close")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_CLOSE)
    public ResponseEntity<Map<String, Object>> closeQuarter(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(closeService.closeQuarter(id, force));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.MOHE_QUARTER_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
