package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.repository.LuTravelFundingSourceRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.wafar.model.WafarSpending;
import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import com.dilaw.mohe.wafar.repository.WafarSpendingCategoryRepository;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIN-02: a Wafar-funded travel ticket is approved and its APPROVED Wafar spend is
 * created automatically. That row had no attaché, and the balance sums approved
 * spends per attaché, so the ticket never reduced anybody's Wafar and the money could
 * be spent twice. It also named the requester as its approver.
 *
 * <p>Here the spends the service saves are kept in a list, and the attaché's balance
 * is read through the real {@link WafarBalanceService} from that same list: after the
 * ticket is applied, {@code availableFor(country)} has fallen by the ticket total.
 */
class TravelTicketWafarSpendTest {

    private static final int JORDAN = 1004;
    private static final int STUDENT = 55;
    private static final int REQUESTER = 13;
    private static final int APPROVER = 14;

    private final List<WafarSpending> saved = new ArrayList<>();
    private WafarSpendingRepository spendingRepository;
    private StudentDataRepository studentDataRepository;
    private WafarBalanceService balance;
    private TravelTicketWafarService service;

    @BeforeEach
    void setUp() {
        spendingRepository = mock(WafarSpendingRepository.class);
        studentDataRepository = mock(StudentDataRepository.class);
        WafarSpendingCategoryRepository categories = mock(WafarSpendingCategoryRepository.class);
        StudentFinanceWafarRepository deposits = mock(StudentFinanceWafarRepository.class);

        WafarSpendingCategory travel = new WafarSpendingCategory();
        travel.setId(9);
        travel.setCategoryCode("TRAVEL_TICKET");
        when(categories.findByCategoryCode("TRAVEL_TICKET")).thenReturn(Optional.of(travel));
        when(studentDataRepository.findStudyCountryInByStudentId(STUDENT)).thenReturn(Optional.of(JORDAN));
        when(spendingRepository.findByTravelTicketIdAndStatusNot(anyInt(), eq(WafarSpendingStatus.CANCELLED)))
                .thenAnswer(inv -> saved.stream()
                        .filter(s -> s.getTravelTicketId().equals(inv.getArgument(0))
                                && !WafarSpendingStatus.CANCELLED.equals(s.getStatus()))
                        .toList());
        when(spendingRepository.save(any(WafarSpending.class))).thenAnswer(inv -> {
            WafarSpending s = inv.getArgument(0);
            if (!saved.contains(s)) saved.add(s);
            return s;
        });
        // The balance: 1000 deposited at Jordan, minus Jordan's approved spends on file.
        when(deposits.sumTotalDeposits(eq(false), eq(List.of(JORDAN)))).thenReturn(new BigDecimal("1000"));
        when(spendingRepository.sumByStatus(eq(WafarSpendingStatus.APPROVED), eq(false), eq(List.of(JORDAN))))
                .thenAnswer(inv -> saved.stream()
                        .filter(s -> WafarSpendingStatus.APPROVED.equals(s.getStatus()))
                        .filter(s -> Integer.valueOf(JORDAN).equals(s.getStudyCountryInId()))
                        .map(WafarSpending::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        balance = new WafarBalanceService();
        ReflectionTestUtils.setField(balance, "depositRepository", deposits);
        ReflectionTestUtils.setField(balance, "spendingRepository", spendingRepository);
        ReflectionTestUtils.setField(balance, "countryScopeService", mock(CountryScopeService.class));

        service = new TravelTicketWafarService(mock(LuTravelFundingSourceRepository.class), balance,
                spendingRepository, categories, studentDataRepository);
    }

    private static StudentTravelTicket ticket(String cost) {
        StudentTravelTicket t = new StudentTravelTicket();
        t.setTravelTicketId(501);
        t.setStudentId(STUDENT);
        t.setTicketCost(new BigDecimal(cost));
        return t;
    }

    @Test
    @DisplayName("FIN-02: after the ticket is applied the attaché's available Wafar falls by the ticket total")
    void ticketDrawsDownTheAttachesWafar() {
        assertThat(balance.availableFor(JORDAN)).isEqualByComparingTo("1000");

        StudentTravelTicket t = ticket("350");
        service.assertBalanceCovers(TravelTicketWafarService.computeTotal(t), t.getTravelTicketId(), STUDENT);
        service.applyTravelTicketSpending(t, TravelTicketWafarService.computeTotal(t), REQUESTER, APPROVER);

        assertThat(balance.availableFor(JORDAN)).isEqualByComparingTo("650");
    }

    @Test
    @DisplayName("FIN-02: the spend names the student's attaché, the requester as maker and the approver as checker")
    void spendCarriesCountryAndApprover() {
        service.applyTravelTicketSpending(ticket("350"), new BigDecimal("350"), REQUESTER, APPROVER);

        ArgumentCaptor<WafarSpending> row = ArgumentCaptor.forClass(WafarSpending.class);
        verify(spendingRepository).save(row.capture());
        assertThat(row.getValue().getStudyCountryInId()).isEqualTo(JORDAN);
        assertThat(row.getValue().getCreatedBy()).isEqualTo(REQUESTER);
        assertThat(row.getValue().getApprovedBy()).isEqualTo(APPROVER);
        assertThat(row.getValue().getStatus()).isEqualTo(WafarSpendingStatus.APPROVED);
    }

    @Test
    @DisplayName("FIN-01: a ticket that costs nothing draws nothing and releases an earlier draw-down")
    void zeroTotalReleases() {
        service.applyTravelTicketSpending(ticket("350"), new BigDecimal("350"), REQUESTER, APPROVER);
        service.applyTravelTicketSpending(ticket("0"), BigDecimal.ZERO, REQUESTER, APPROVER);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(WafarSpendingStatus.CANCELLED);
        assertThat(balance.availableFor(JORDAN)).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("FIN-02: a student with no attaché on record cannot be paid from Wafar")
    void noAttacheRefused() {
        when(studentDataRepository.findStudyCountryInByStudentId(STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyTravelTicketSpending(ticket("350"), new BigDecimal("350"), REQUESTER, APPROVER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(spendingRepository, never()).save(any());
    }
}
