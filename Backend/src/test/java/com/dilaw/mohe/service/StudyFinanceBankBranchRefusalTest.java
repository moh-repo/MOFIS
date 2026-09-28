package com.dilaw.mohe.service;

import com.dilaw.mohe.exception.ErrorDetails;
import com.dilaw.mohe.exception.GlobalExceptionHandler;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.repository.StudyFinanceBankBranchRepository;
import com.dilaw.mohe.repository.StudyFinanceBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * The bank-branch screen now shows the server's reason when a save is refused,
 * so the branch service's own two refusals must reach the reader in their
 * language. They were English sentences, one naming the field
 * ({@code studyCountryInId}) to Arabic readers too. Both are raised before the
 * duplicate-name check and the access policy, so the service runs here on mocks.
 */
@ExtendWith(MockitoExtension.class)
class StudyFinanceBankBranchRefusalTest {

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;

    @Mock private StudyFinanceBankBranchRepository repository;
    @Mock private StudyFinanceBankRepository bankRepository;
    @InjectMocks private StudyFinanceBankBranchService service;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void wireRealBundles() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(handler, "messageSource", messages);
    }

    private String answer(LocalizedStatusException ex, String lang) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mohe/api/v1/studyFinanceBankBranch/");
        req.addHeader("Accept-Language", lang);
        ResponseEntity<?> response = handler.responseStatusException(ex, new ServletWebRequest(req));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return ((ErrorDetails) response.getBody()).getMessage();
    }

    private static StudyFinanceBankBranch draft(Integer bankId, Integer countryId) {
        StudyFinanceBankBranch b = new StudyFinanceBankBranch();
        b.setStudyFinanceBankBranchName("QA");
        b.setStudyFinanceBankeId(bankId);
        b.setStudyCountryInId(countryId);
        return b;
    }

    @Test
    @DisplayName("a bank of another country: refused in the reader's language")
    void bankOfAnotherCountry() {
        StudyFinanceBank saudiBank = new StudyFinanceBank();
        saudiBank.setStudyCountryInId(SAUDI);
        when(repository.nextId()).thenReturn(1);
        when(bankRepository.findById(1000)).thenReturn(Optional.of(saudiBank));

        LocalizedStatusException ex = catchThrowableOfType(LocalizedStatusException.class,
                () -> service.create(draft(1000, JORDAN)));

        assertThat(ex.messageKey()).isEqualTo("bankBranch.countryMismatch");
        assertThat(answer(ex, "en")).isEqualTo(
                "The chosen bank belongs to a different country. A branch must be in the same country as its bank.");
        assertThat(answer(ex, "ar")).startsWith("البنك المختار يتبع دولة أخرى");
    }

    @Test
    @DisplayName("round-6 R6-04: a bank that does not exist is refused, not read as no bank")
    void bankThatDoesNotExist() {
        when(repository.nextId()).thenReturn(1);
        when(bankRepository.findById(999999)).thenReturn(Optional.empty());

        LocalizedStatusException ex = catchThrowableOfType(LocalizedStatusException.class,
                () -> service.create(draft(999999, JORDAN)));

        assertThat(ex.messageKey()).isEqualTo("bankBranch.bankMissing");
        assertThat(answer(ex, "en")).isEqualTo("The bank this branch names does not exist. Choose a bank from the list.");
        assertThat(answer(ex, "ar")).startsWith("البنك المحدد لهذا الفرع غير موجود");
    }

    @Test
    @DisplayName("no country and no bank to take one from: refused without naming the field")
    void noCountry() {
        when(repository.nextId()).thenReturn(1);

        LocalizedStatusException ex = catchThrowableOfType(LocalizedStatusException.class,
                () -> service.create(draft(null, null)));

        assertThat(ex.messageKey()).isEqualTo("bankBranch.countryRequired");
        assertThat(ex.getReason()).as("the English reason stays for the log").contains("studyCountryInId");
        assertThat(answer(ex, "en")).startsWith("Choose the country").doesNotContain("studyCountryInId");
        assertThat(answer(ex, "ar")).startsWith("اختر الدولة").doesNotContain("studyCountryInId");
    }
}
