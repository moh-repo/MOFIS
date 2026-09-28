package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.model.StudentTravelTicketFamilyMember;
import com.dilaw.mohe.repository.LuTravelFundingSourceRepository;
import com.dilaw.mohe.wafar.model.WafarSpending;
import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import com.dilaw.mohe.wafar.repository.WafarSpendingCategoryRepository;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Bridges student travel tickets to the Wafar spending ledger: verifies the Wafar balance covers a
 * ticket's total and creates/updates/reverses the linked {@link WafarSpending} row so that a
 * Wafar-funded ticket actually draws down the balance.
 *
 * <p>Note: Wafar spending is a single amount with no currency of its own; the ticket total is
 * recorded as-is (no FX conversion between the ticket currency and the Wafar base currency).</p>
 */
@Service
@RequiredArgsConstructor
public class TravelTicketWafarService {

    public static final String WAFAR_CODE = "WAFAR";
    private static final String CATEGORY_CODE = "TRAVEL_TICKET";
    private static final String STATUS_APPROVED = WafarSpendingStatus.APPROVED;
    private static final String STATUS_CANCELLED = WafarSpendingStatus.CANCELLED;

    private final LuTravelFundingSourceRepository fundingSourceRepository;
    private final WafarBalanceService balanceService;
    private final WafarSpendingRepository spendingRepository;
    private final WafarSpendingCategoryRepository categoryRepository;
    private final com.dilaw.mohe.repository.StudentDataRepository studentDataRepository;

    /** Total ticket cost = student's own cost + sum of family member costs. */
    public static BigDecimal computeTotal(StudentTravelTicket ticket) {
        if (ticket == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = ticket.getTicketCost() != null ? ticket.getTicketCost() : BigDecimal.ZERO;
        List<StudentTravelTicketFamilyMember> members = ticket.getFamilyMembers();
        if (members != null) {
            for (StudentTravelTicketFamilyMember m : members) {
                if (m != null && m.getTicketCost() != null) {
                    total = total.add(m.getTicketCost());
                }
            }
        }
        return total;
    }

    public boolean isWafarSource(Integer fundingSourceId) {
        if (fundingSourceId == null) {
            return false;
        }
        return fundingSourceRepository.findById(fundingSourceId)
                .map(f -> WAFAR_CODE.equalsIgnoreCase(f.getFundingSourceCode()))
                .orElse(false);
    }

    /**
     * Ensure the Wafar balance covers the *additional* amount this ticket needs. For a brand-new
     * ticket (ticketId null) that is the whole total; for an edit it is total minus whatever the
     * ticket has already committed, so re-approving an unchanged ticket never fails.
     */
    public void assertBalanceCovers(BigDecimal total, Integer ticketId, Integer studentId) {
        BigDecimal needed = (total == null ? BigDecimal.ZERO : total).subtract(alreadyCommitted(ticketId));
        if (needed.signum() <= 0) {
            return;
        }
        // The student's attaché, not the caller's scope (live QA M1): a ministry approver
        // was checked against the ministry-wide total.
        Integer attache = studentId == null ? null
                : studentDataRepository.findStudyCountryInByStudentId(studentId).orElse(null);
        balanceService.requireCovers(attache, needed);
    }

    /**
     * Create or update the single Wafar spending linked to this ticket, as an APPROVED draw-down.
     *
     * <p>FIN-02: the row carries the student's attaché. It used to carry none, and the
     * balance ({@link WafarBalanceService#availableFor}) sums approved spends <em>per
     * attaché</em>, so a Wafar-funded ticket never reduced anybody's balance and the same
     * money could be spent again. The approver of record is whoever signed the ticket
     * request, not the officer who raised it; the raiser stays the maker.
     *
     * @param requesterUserId who raised the ticket request (the maker, CREATED_BY)
     * @param approverUserId  who gave the final approval (APPROVED_BY); the requester when unknown
     */
    @Transactional
    public void applyTravelTicketSpending(StudentTravelTicket ticket, BigDecimal total,
                                          Integer requesterUserId, Integer approverUserId) {
        if (ticket == null || ticket.getTravelTicketId() == null) {
            return;
        }
        Integer ticketId = ticket.getTravelTicketId();
        if (total == null || total.signum() <= 0) {
            // FIN-01: a spend is strictly positive. A ticket that costs nothing draws
            // nothing, so any earlier draw-down for it is released instead.
            reverseTravelTicketSpending(ticketId);
            return;
        }
        Integer attache = ticket.getStudentId() == null ? null
                : studentDataRepository.findStudyCountryInByStudentId(ticket.getStudentId()).orElse(null);
        if (attache == null) {
            // The same refusal requireCovers gives: with no attaché there is no balance
            // to draw from, and a row with none is invisible to every balance.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The student of travel ticket " + ticketId + " has no attaché on record, "
                            + "so the ticket cannot be paid from Wafar.");
        }
        WafarSpendingCategory category = categoryRepository.findByCategoryCode(CATEGORY_CODE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Wafar spending category '" + CATEGORY_CODE + "' not found. "
                                + "Apply migration V058__reconcile_hand_applied_schema.sql, which seeds it."));

        WafarSpending spending = spendingRepository.findByTravelTicketIdAndStatusNot(ticketId, STATUS_CANCELLED)
                .stream().findFirst().orElseGet(WafarSpending::new);
        boolean isNew = spending.getId() == null;

        spending.setTravelTicketId(ticketId);
        spending.setCategory(category);
        spending.setStudentId(ticket.getStudentId());
        spending.setStudyCountryInId(attache);
        spending.setBeneficiaryType(com.dilaw.mohe.codes.WafarBeneficiaryType.STUDENT);
        spending.setAmount(total);
        spending.setSpendingDate(ticket.getTravelDate() != null ? ticket.getTravelDate() : LocalDate.now());
        spending.setReferenceNumber(ticket.getTicketNumber());
        spending.setDescriptionEn("Travel ticket #" + ticketId);
        spending.setStatus(STATUS_APPROVED);
        spending.setApprovedBy(approverUserId != null ? approverUserId : requesterUserId);
        spending.setApprovedAt(Instant.now());
        if (isNew) {
            spending.setCreatedBy(requesterUserId);
        }
        spendingRepository.save(spending);
    }

    /** Cancel the ticket's linked spending (releases the funds) — used when funding leaves Wafar or the ticket is deleted. */
    @Transactional
    public void reverseTravelTicketSpending(Integer ticketId) {
        if (ticketId == null) {
            return;
        }
        for (WafarSpending s : spendingRepository.findByTravelTicketIdAndStatusNot(ticketId, STATUS_CANCELLED)) {
            s.setStatus(STATUS_CANCELLED);
            s.setRejectionReason("Travel ticket funding removed or ticket deleted");
            spendingRepository.save(s);
        }
    }

    private BigDecimal alreadyCommitted(Integer ticketId) {
        if (ticketId == null) {
            return BigDecimal.ZERO;
        }
        return spendingRepository.findByTravelTicketIdAndStatusNot(ticketId, STATUS_CANCELLED).stream()
                .map(WafarSpending::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
