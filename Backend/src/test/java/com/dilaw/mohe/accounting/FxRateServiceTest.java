package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.FxRate;
import com.dilaw.mohe.accounting.repository.FxRateRepository;
import com.dilaw.mohe.accounting.service.FxRateService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exchange rates: a rate is dated, positive, between two different currencies,
 * and never guessed.
 */
@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock private FxRateRepository rateRepository;
    @Mock private CountryScopeService countryScopeService;
    @InjectMocks private FxRateService service;

    private static FxRate rate(int id, int from, int to, String date, String value) {
        FxRate r = new FxRate();
        r.setId(id);
        r.setFromCurrencyId(from);
        r.setToCurrencyId(to);
        r.setRateDate(LocalDate.parse(date));
        r.setRate(new BigDecimal(value));
        return r;
    }

    @Test
    @DisplayName("the list narrows by pair and by date, newest first")
    void listFilters() {
        when(rateRepository.findAllByOrderByRateDateDescFromCurrencyIdAscToCurrencyIdAsc()).thenReturn(List.of(
                rate(3, 1, 1012, "2026-03-01", "530"),
                rate(2, 1, 1012, "2026-02-01", "525"),
                rate(1, 1008, 1012, "2026-02-01", "140")));

        assertThat(service.list(1, 1012, null)).extracting(FxRate::getId).containsExactly(3, 2);
        assertThat(service.list(null, null, LocalDate.parse("2026-02-15")))
                .as("a rate dated after the day asked for was not in force on it")
                .extracting(FxRate::getId).containsExactly(2, 1);
        assertThat(service.list(null, null, null)).hasSize(3);
    }

    @Test
    @DisplayName("the effective rate is the latest on or before the day, and absent rather than 1.0 when none")
    void effectiveIsLatestOnOrBefore() {
        LocalDate day = LocalDate.parse("2026-02-15");
        when(rateRepository.findFirstByFromCurrencyIdAndToCurrencyIdAndRateDateLessThanEqualOrderByRateDateDesc(1, 1012, day))
                .thenReturn(Optional.of(rate(2, 1, 1012, "2026-02-01", "525")));
        when(rateRepository.findFirstByFromCurrencyIdAndToCurrencyIdAndRateDateLessThanEqualOrderByRateDateDesc(1008, 1012, day))
                .thenReturn(Optional.empty());

        assertThat(service.effective(1, 1012, day)).map(FxRate::getId).contains(2);
        assertThat(service.effective(1008, 1012, day))
                .as("a missing rate stops a translation; an invented one silently misstates it")
                .isEmpty();
    }

    @Test
    @DisplayName("a rate must be positive and between two different currencies")
    void createValidates() {
        assertThatThrownBy(() -> service.create(rate(0, 1, 1, "2026-02-01", "1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("against itself");
        assertThatThrownBy(() -> service.create(rate(0, 1, 1012, "2026-02-01", "0")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("positive");
        verify(rateRepository, never()).save(any());
    }

    @Test
    @DisplayName("one rate per pair per day: a second is a conflict naming the first")
    void createRefusesDuplicateDay() {
        when(rateRepository.findByFromCurrencyIdAndToCurrencyIdAndRateDate(1, 1012, LocalDate.parse("2026-02-01")))
                .thenReturn(Optional.of(rate(2, 1, 1012, "2026-02-01", "525")));

        assertThatThrownBy(() -> service.create(rate(0, 1, 1012, "2026-02-01", "526")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("#2")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(rateRepository, never()).save(any());
    }

    @Test
    @DisplayName("create stamps the signed-in user and ignores any id in the body")
    void createStampsUser() {
        when(rateRepository.findByFromCurrencyIdAndToCurrencyIdAndRateDate(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(13L));
        when(rateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FxRate saved = service.create(rate(42, 1, 1012, "2026-02-01", "525"));

        assertThat(saved.getId()).as("a POST creates; it does not overwrite #42").isNull();
        assertThat(saved.getCreatedBy()).isEqualTo(13);
    }
}
