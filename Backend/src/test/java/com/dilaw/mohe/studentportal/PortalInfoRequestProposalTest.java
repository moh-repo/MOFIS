package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.service.StudentInfoRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * System test PRT-05: an answer to an information request may propose only the fields
 * its kind allows. The portal's "include my data" sends the whole record back, so an
 * unchanged field outside the list is dropped; a changed one is a 400.
 */
class PortalInfoRequestProposalTest {

    private static final int STUDENT = 31;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private StudentInfoRequestService service;
    private StudentInfoRequest request;

    @BeforeEach
    void setUp() {
        service = new StudentInfoRequestService();
        StudentDataRepository students = mock(StudentDataRepository.class);
        StudentFinanceRepository finances = mock(StudentFinanceRepository.class);
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "studentFinanceRepository", finances);
        ReflectionTestUtils.setField(service, "jsonMapper", mapper);

        StudentDatum stored = new StudentDatum();
        stored.setStudentId(STUDENT);
        stored.setStudentAmount(500);
        stored.setStudentCellphone("+967-771-000-000");
        stored.setStudyCountryIn(1004);
        stored.setStudentBirthdate(Instant.parse("2000-01-01T00:00:00Z"));
        when(students.findById(STUDENT)).thenReturn(Optional.of(stored));
        StudentFinance finance = new StudentFinance();
        finance.setId(STUDENT);
        finance.setStudentFinRecievedAmount(1200);
        when(finances.findById(STUDENT)).thenReturn(Optional.of(finance));

        request = new StudentInfoRequest();
        request.setStudentId(STUDENT);
        request.setRequestType("STATUS_CERT");
    }

    private String studentJson(int amount, String cellphone) {
        return "{\"studentId\":" + STUDENT + ",\"studentAmount\":" + amount + ",\"studentCellphone\":\"" + cellphone
                + "\",\"studyCountryIn\":1004,\"studentBirthdate\":\"2000-01-01T00:00:00Z\",\"amountCurrencyName\":\"USD\"}";
    }

    @Test
    @DisplayName("a changed studentAmount is refused with a 400 naming it")
    void amountRefused() {
        assertThatThrownBy(() -> service.checkPortalProposal(request, studentJson(9_999, "+967-771-000-000"), null))
                .isInstanceOf(LocalizedStatusException.class)
                .satisfies(e -> {
                    LocalizedStatusException l = (LocalizedStatusException) e;
                    assertThat(l.getStatusCode().value()).isEqualTo(400);
                    assertThat(l.messageKey()).isEqualTo("portal.infoRequest.fieldNotAllowed");
                    assertThat(l.messageArgs()).containsExactly("studentAmount");
                });
    }

    @Test
    @DisplayName("the whole record sent back unchanged but for a phone: only the phone is staged")
    void onlyPermittedFieldsStaged() throws Exception {
        StudentInfoRequestService.PortalProposal p =
                service.checkPortalProposal(request, studentJson(500, "+967-772-111-111"), null);
        assertThat(mapper.readTree(p.studentDataJson()).properties())
                .extracting(java.util.Map.Entry::getKey).containsExactly("studentCellphone");
        assertThat(p.studentFinanceJson()).isNull();
    }

    @Test
    @DisplayName("the study country cannot be moved through an answer, whatever the kind")
    void countryRefused() {
        request.setRequestType("OTHER");
        String json = "{\"studyCountryIn\":1007}";
        assertThatThrownBy(() -> service.checkPortalProposal(request, json, null))
                .isInstanceOf(LocalizedStatusException.class);
    }

    @Test
    @DisplayName("no finance field may be changed from the portal; an unchanged one is dropped")
    void financeRefusedOrDropped() {
        assertThatThrownBy(() -> service.checkPortalProposal(request, null, "{\"id\":31,\"studentFinRecievedAmount\":5000}"))
                .isInstanceOf(LocalizedStatusException.class);
        StudentInfoRequestService.PortalProposal p =
                service.checkPortalProposal(request, null, "{\"id\":31,\"studentFinRecievedAmount\":1200,\"received\":true}");
        assertThat(p.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("data that is not a JSON object is a 400")
    void malformed() {
        assertThatThrownBy(() -> service.checkPortalProposal(request, "[1,2]", null))
                .isInstanceOf(LocalizedStatusException.class)
                .satisfies(e -> assertThat(((LocalizedStatusException) e).messageKey()).isEqualTo("portal.infoRequest.proposalInvalid"));
    }
}
