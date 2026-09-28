package com.dilaw.mohe.wafar;

import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-5 QA R5-18: PUT /wafar/transactions/{id} with the spend exactly as GET returns it
 * answered 500. GET carries the category as an object, the edit body read categoryId, and
 * findById(null) threw. The body as GET returns it is now accepted; a body naming no
 * category is a 400.
 */
class WafarSpendingPutBodyTest {

    /** Spring Boot's JSON mapper settings (unknown properties such as status are ignored). */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private static final String AS_GET_RETURNS_IT = """
            {"id": 167, "category": {"id": 3, "categoryCode": "RENT", "nameEn": "Rent"},
             "amount": 10.00, "spendingDate": "2026-09-26", "referenceNumber": "QAR5-M1-3",
             "studentId": 10119, "beneficiaryType": "STUDENT", "studyCountryInId": 1004,
             "status": "PENDING", "createdBy": 13, "createdAt": "2026-09-26T17:00:00Z"}
            """;

    @Test
    @DisplayName("the spend as GET returns it reads as an edit of category 3")
    void getBodyIsAnEditBody() throws Exception {
        WafarSpendingCreateDto dto = mapper.readValue(AS_GET_RETURNS_IT, WafarSpendingCreateDto.class);
        assertThat(dto.getCategoryId()).isEqualTo(3);
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(dto.getStudyCountryInId()).isEqualTo(1004);
    }

    @Test
    @DisplayName("an explicit categoryId wins over the nested object, in either order")
    void explicitIdWins() throws Exception {
        assertThat(mapper.readValue("{\"categoryId\": 5, \"category\": {\"id\": 3}}", WafarSpendingCreateDto.class)
                .getCategoryId()).isEqualTo(5);
        assertThat(mapper.readValue("{\"category\": {\"id\": 3}, \"categoryId\": 5}", WafarSpendingCreateDto.class)
                .getCategoryId()).isEqualTo(5);
    }

    @Test
    @DisplayName("an edit or a new spend with no category is a 400, not a 500")
    void noCategoryIs400() {
        WafarSpendingService service = new WafarSpendingService();
        for (Runnable call : new Runnable[] {
                () -> service.update(167, new WafarSpendingCreateDto()),
                () -> service.create(new WafarSpendingCreateDto())}) {
            assertThatThrownBy(call::run).isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
