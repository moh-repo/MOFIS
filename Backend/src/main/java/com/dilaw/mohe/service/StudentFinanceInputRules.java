package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentFinance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.exception.LocalizedMessage;

/**
 * What a finance record must carry before it is raised for approval (review item F3).
 *
 * <p>The finance form checks these on submit, but the endpoint accepted anything: an
 * amount of 0, no status, no country. {@code @Valid} on the controller was a no-op
 * because the entity has no constraints, and constraints cannot be added there
 * without breaking saves of legacy rows (Hibernate validates the entity on every
 * persist, including the quarter rollover's). So the rules live here and are checked
 * on the two endpoints that take a whole record from the form.
 */
public final class StudentFinanceInputRules {

    private StudentFinanceInputRules() {
    }

    /**
     * The statuses a cheque date is required for when the lookup is not at hand: the
     * ids V072 seeded for RECEIVED, DISBURSED and PARTIALLY_DISBURSED. The controllers
     * pass the set resolved by CODE ({@code StudyFinReceivedStatusService.paidStatusIds}).
     */
    public static final IntPredicate CHECK_DATE_REQUIRED_FALLBACK =
            id -> StudyFinReceivedStatusService.PAID_IDS_FALLBACK.contains(id);

    /** The received year is a calendar year; 0 or none means "not set". */
    public static final int MIN_RECEIVED_YEAR = 1990;
    public static final int MAX_RECEIVED_YEAR = 2100;

    private static final String RECEIVED_YEAR = "received year (a year between 1990 and 2100)";

    /**
     * Whether a received year is a calendar year or unset (live QA H7: a YYYYQ quarter
     * key, 20251, was stored as the year and showed up as a year on the dashboard).
     */
    public static boolean isReceivedYear(Integer year) {
        return year == null || year == 0 || (year >= MIN_RECEIVED_YEAR && year <= MAX_RECEIVED_YEAR);
    }

    /**
     * The received-year rule on its own, for the service's save paths (approval
     * execution included), which do not run the whole form check.
     *
     * @throws IllegalArgumentException (400) in the reader's language
     */
    public static void requireReceivedYear(StudentFinance f) {
        if (f != null && !isReceivedYear(f.getStudentFinRecievedYear())) {
            throw new LocalizedArgumentException(
                    "Finance record is incomplete or invalid: " + RECEIVED_YEAR + ", got " + f.getStudentFinRecievedYear(),
                    "finance.incomplete", List.of(LocalizedMessage.key(KEYS.get(RECEIVED_YEAR), RECEIVED_YEAR)));
        }
    }

    /** As {@link #problems(StudentFinance, IntPredicate)} with the fallback set of paid statuses. */
    public static List<String> problems(StudentFinance f) {
        return problems(f, CHECK_DATE_REQUIRED_FALLBACK);
    }

    /**
     * The fields that break a rule, in form order; empty when the record is acceptable.
     *
     * <p>The cheque date is required only for a status that means money moved
     * ({@code checkDateRequiredFor}). It used to be required of every record, which
     * refused 976 of 983 rows on file the moment they were saved back unchanged — a
     * "Not Specified" row has no cheque to date (live QA H2, 23 Sep 2026). A status of
     * 0 or none is still refused: NOT_SPECIFIED exists for "unset", and V135 moved the
     * legacy rows onto it.
     *
     * @param checkDateRequiredFor whether a status id needs a cheque date
     */
    public static List<String> problems(StudentFinance f, IntPredicate checkDateRequiredFor) {
        List<String> problems = new ArrayList<>();
        if (f == null) {
            problems.add("record");
            return problems;
        }
        if (f.getId() == null || f.getId() <= 0) {
            problems.add("student");
        }
        if (f.getStudentFinRecievedAmount() == null || f.getStudentFinRecievedAmount() <= 0) {
            problems.add("amount (must be greater than 0)");
        }
        Integer status = f.getStudyFinRecievedStatus();
        if (status != null && status > 0 && checkDateRequiredFor.test(status) && f.getStudyCheckDate() == null) {
            problems.add("check date");
        }
        if (status == null || status <= 0) {
            problems.add("received status");
        }
        if (f.getStudyCountryInId() == null || f.getStudyCountryInId() <= 0) {
            problems.add("study country");
        }
        if (!isReceivedYear(f.getStudentFinRecievedYear())) {
            problems.add(RECEIVED_YEAR);
        }
        Integer wafar = f.getWafarAmount();
        if (wafar != null && wafar < 0) {
            problems.add("wafar amount (must not be negative)");
        }
        if (wafar != null && f.getStudentFinRecievedAmount() != null && wafar > f.getStudentFinRecievedAmount()) {
            problems.add("wafar amount (must not exceed the amount)");
        }
        return problems;
    }

    /** Each problem's message key, for the reader's language (W3-08); the English above is the fallback. */
    private static final java.util.Map<String, String> KEYS = java.util.Map.of(
            "record", "finance.rule.record",
            "student", "finance.rule.student",
            "amount (must be greater than 0)", "finance.rule.amount",
            "check date", "finance.rule.checkDate",
            "received status", "finance.rule.status",
            "study country", "finance.rule.country",
            RECEIVED_YEAR, "finance.rule.receivedYear",
            "wafar amount (must not be negative)", "finance.rule.wafarNegative",
            "wafar amount (must not exceed the amount)", "finance.rule.wafarTooHigh");

    /** As {@link #require(StudentFinance, IntPredicate)} with the fallback set of paid statuses. */
    public static void require(StudentFinance f) {
        require(f, CHECK_DATE_REQUIRED_FALLBACK);
    }

    /** @throws IllegalArgumentException (400) naming every field that breaks a rule, in the reader's language */
    public static void require(StudentFinance f, IntPredicate checkDateRequiredFor) {
        List<String> problems = problems(f, checkDateRequiredFor);
        if (!problems.isEmpty()) {
            List<Object> reasons = problems.stream()
                    .map(p -> (Object) LocalizedMessage.key(KEYS.getOrDefault(p, "finance.rule." + p), p))
                    .toList();
            throw new LocalizedArgumentException(
                    "Finance record is incomplete or invalid: " + String.join(", ", problems),
                    "finance.incomplete", reasons);
        }
    }
}
