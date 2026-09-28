package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.model.StudentInfoRequestKind;
import com.dilaw.mohe.repository.StudentInfoRequestKindRepository;
import com.dilaw.mohe.repository.StudentInfoRequestRepository;
import com.dilaw.mohe.exception.LocalizedMessage;
import com.dilaw.mohe.exception.LocalizedStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Round-5 QA R5-26: the staff create endpoint accepted a request type that is not on the
 * list (GENERAL), and the portal then showed the raw code in both languages. Decision
 * D-8: a type must be one of the active kinds; a request that names none is OTHER.
 */
class InfoRequestKindTest {

    private StudentInfoRequestRepository requests;
    private StudentInfoRequestService service;

    private static StudentInfoRequestKind kind(String code, boolean active) {
        StudentInfoRequestKind k = new StudentInfoRequestKind();
        k.setKindCode(code);
        k.setIsActive(active);
        return k;
    }

    @BeforeEach
    void setUp() {
        StudentInfoRequestKindRepository kinds = Mockito.mock(StudentInfoRequestKindRepository.class);
        Mockito.when(kinds.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                kind("STATUS_CERT", true), kind("BANK_COVER_LETTER", true), kind("OTHER", true), kind("OLD_KIND", false)));
        requests = Mockito.mock(StudentInfoRequestRepository.class);
        Mockito.when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new StudentInfoRequestService();
        ReflectionTestUtils.setField(service, "repository", requests);
        ReflectionTestUtils.setField(service, "kindRepository", kinds);
    }

    @Test
    @DisplayName("the QA case: GENERAL is refused with 400 naming the kinds, and nothing is saved")
    void unknownKindRefused() {
        assertThatThrownBy(() -> service.createRequests("GENERAL", "t", null, null, "user-1", List.of(1, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("GENERAL").hasMessageContaining("STATUS_CERT")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("round-6 R6-02: the refusal is answered in the reader's language, from the real bundles")
    void unknownKindRefusalIsLocalized() {
        LocalizedStatusException refusal = catchThrowableOfType(LocalizedStatusException.class,
                () -> service.createRequests("GENERAL", "t", null, null, "user-1", List.of(1)));
        assertThat(refusal.messageKey()).isEqualTo("infoRequest.unknownType");

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        String arabic = LocalizedMessage.resolve(messages, refusal, Locale.forLanguageTag("ar"));
        String english = LocalizedMessage.resolve(messages, refusal, Locale.ENGLISH);

        assertThat(arabic).startsWith("نوع الطلب").contains("'GENERAL'").contains("STATUS_CERT، BANK_COVER_LETTER، OTHER");
        assertThat(english).isEqualTo(
                "Unknown request type 'GENERAL'. Choose one of: STATUS_CERT, BANK_COVER_LETTER, OTHER.");
    }

    @Test
    @DisplayName("a switched-off kind and the old CUSTOM default are refused too")
    void inactiveAndCustomRefused() {
        assertThatThrownBy(() -> service.createRequests("OLD_KIND", "t", null, null, "user-1", List.of(1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createRequests("CUSTOM", "t", null, null, "user-1", List.of(1)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a kind on the list is stored; no kind at all is stored as OTHER")
    void listedKindAndDefault() {
        List<StudentInfoRequest> made = service.createRequests("STATUS_CERT", "t", null, null, "user-1", List.of(1));
        assertThat(made).extracting(StudentInfoRequest::getRequestType).containsExactly("STATUS_CERT");
        List<StudentInfoRequest> none = service.createRequests(null, "t", null, null, "user-1", List.of(1));
        assertThat(none).extracting(StudentInfoRequest::getRequestType).containsExactly("OTHER");
        List<StudentInfoRequest> blank = service.createRequests("  ", "t", null, null, "user-1", List.of(1));
        assertThat(blank).extracting(StudentInfoRequest::getRequestType).containsExactly("OTHER");
    }
}
