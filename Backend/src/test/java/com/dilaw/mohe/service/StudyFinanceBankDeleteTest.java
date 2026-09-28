package com.dilaw.mohe.service;

import com.dilaw.mohe.exception.ErrorDetails;
import com.dilaw.mohe.exception.GlobalExceptionHandler;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.repository.StudyFinanceBankBranchRepository;
import com.dilaw.mohe.repository.StudyFinanceBankRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-6 QA R6-04: a bank was deleted (204) while a branch still named it, and the
 * branch kept the missing bank id. The delete is refused while any branch names the
 * bank, with a 409 that says how many, in the reader's language. V147 adds the
 * foreign key behind it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyFinanceBankDeleteTest {

    private static final int BANK = 1000;

    @Mock private StudyFinanceBankRepository repository;
    @Mock private StudyFinanceBankBranchRepository branchRepository;
    @Mock private CountryScopeService countryScopeService;
    @InjectMocks private StudyFinanceBankService service;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(new StudyFinanceBank()));
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(handler, "messageSource", messages);
    }

    private String answer(LocalizedStatusException ex, String lang) {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/mohe/api/v1/studyFinanceBank/" + BANK);
        req.addHeader("Accept-Language", lang);
        ResponseEntity<?> response = handler.responseStatusException(ex, new ServletWebRequest(req));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        return ((ErrorDetails) response.getBody()).getMessage();
    }

    @Test
    @DisplayName("the QA case: a bank a branch still names is not deleted, and the answer says how many")
    void bankWithBranchRefused() {
        when(branchRepository.countByStudyFinanceBankeId(BANK)).thenReturn(1L);

        LocalizedStatusException ex = catchThrowableOfType(LocalizedStatusException.class, () -> service.delete(BANK));

        assertThat(ex.messageKey()).isEqualTo("bank.hasBranches");
        assertThat(answer(ex, "en")).startsWith("This bank still has 1 branch(es).");
        assertThat(answer(ex, "ar")).startsWith("لهذا البنك 1 فرع/فروع");
        verify(repository, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("a bank no branch names is deleted")
    void bankWithoutBranchDeleted() {
        when(branchRepository.countByStudyFinanceBankeId(BANK)).thenReturn(0L);

        service.delete(BANK);

        verify(repository).deleteById(BANK);
    }
}
