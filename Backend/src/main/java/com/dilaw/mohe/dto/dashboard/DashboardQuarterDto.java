package com.dilaw.mohe.dto.dashboard;

import com.dilaw.mohe.model.MoheQuarter;

import java.time.Instant;

/**
 * The most recent open MOHE quarter the caller may see, or {@code present = false}.
 *
 * <p>Always a 200 body: the quarter controller answers 204 when there is none,
 * which every client has to special-case; here the flag carries it.
 *
 * @param countryId {@code null} = a global quarter, shared by every country
 */
public record DashboardQuarterDto(
        boolean present,
        Integer quarter,
        Integer quarterYear,
        Integer quarterNumber,
        String quarterName,
        String quarterNameEn,
        boolean quarterClosed,
        Integer countryId,
        Integer quarterAmount,
        Long quarterNetAmount,
        Long budgetAmount,
        Long paidAmount,
        Integer quarterStudentCount,
        Instant quarterPayDate) {

    public static DashboardQuarterDto none() {
        return new DashboardQuarterDto(false, null, null, null, null, null, false, null,
                null, null, null, null, null, null);
    }

    public static DashboardQuarterDto of(MoheQuarter q) {
        return new DashboardQuarterDto(true, q.getQuarter(), q.getQuarterYear(), q.getQuarterNumber(),
                q.getQuarterName(), q.getQuarterNameEn(), Boolean.TRUE.equals(q.getQuarterClosed()),
                q.getCountryId(), q.getQuarterAmount(), q.getQuarterNetAmount(), q.getBudgetAmount(),
                q.getPaidAmount(), q.getQuarterStudentCount(), q.getQuarterPayDate());
    }
}
