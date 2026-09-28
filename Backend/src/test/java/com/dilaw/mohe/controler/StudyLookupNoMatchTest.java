package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudyAmountLookup;
import com.dilaw.mohe.model.StudyYearsLookup;
import com.dilaw.mohe.service.StudyAmountLookupService;
import com.dilaw.mohe.service.StudyYearsLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * "No rule for this degree" is an answer, not a server error.
 *
 * <p>Both endpoints built their reply with {@code Map.of}, which throws on a null
 * value, so a degree and major with no study-years row (or a row whose value is
 * empty) answered 500 and the Add Student auto-fill silently did nothing (live QA #6).
 */
@ExtendWith(MockitoExtension.class)
class StudyLookupNoMatchTest {

    @Mock private StudyYearsLookupService yearsService;
    @Mock private StudyAmountLookupService amountService;
    @InjectMocks private StudyYearsLookupController yearsController;
    @InjectMocks private StudyAmountLookupController amountController;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (Map<String, Object>) response.getBody();
    }

    @Test
    @DisplayName("study-years with no matching rule answers 200 with a null value")
    void studyYearsNoMatch() {
        when(yearsService.resolve(anyInt(), anyInt(), any())).thenReturn(Optional.empty());

        Map<String, Object> body = body(yearsController.getStudyYears(1, 2, 1));

        assertThat(body).containsKey("studyYears");
        assertThat(body.get("studyYears")).isNull();
    }

    @Test
    @DisplayName("study-years whose stored value is empty answers 200 with a null value")
    void studyYearsEmptyValue() {
        when(yearsService.resolve(anyInt(), anyInt(), any())).thenReturn(Optional.of(new StudyYearsLookup()));

        assertThat(body(yearsController.getStudyYears(1, 2, 1)).get("studyYears")).isNull();
    }

    @Test
    @DisplayName("study-amount with no matching rule answers 200 with a null amount")
    void studyAmountNoMatch() {
        when(amountService.resolve(anyInt(), anyInt(), any())).thenReturn(Optional.empty());

        Map<String, Object> body = body(amountController.getStudyAmount(1, 2, 1));

        assertThat(body).containsKey("amount");
        assertThat(body.get("amount")).isNull();
        assertThat(body).doesNotContainKey("currencyId");
    }

    @Test
    @DisplayName("study-amount with a rule but no amount answers 200, currency defaulted")
    void studyAmountEmptyValue() {
        when(amountService.resolve(anyInt(), anyInt(), any())).thenReturn(Optional.of(new StudyAmountLookup()));

        Map<String, Object> body = body(amountController.getStudyAmount(1, 2, 1));

        assertThat(body.get("amount")).isNull();
        assertThat(body.get("currencyId")).isEqualTo(0);
    }
}
