package com.dilaw.mohe;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NET_AMOUNT is a PERSISTED computed column, so the database owns the arithmetic and
 * nothing in the application writes it. That leaves exactly one thing application code
 * can still get wrong: letting a value in from outside.
 *
 * StudentFinance is round-tripped through the approval payload —
 *
 *   ApprovalIntegrationService  -> objectMapper.writeValueAsString(proposedData)
 *   ApprovalRequest.requestData -> stored
 *   executeStudentFinanceRequest -> objectMapper.readValue(..., StudentFinance.class)
 *
 * — and the entity is also bound straight from request bodies. Without
 * {@code @JsonProperty(access = READ_ONLY)} a caller could submit a netAmount that
 * contradicts the amount and the wafar, and it would sit in the stored payload looking
 * authoritative. It must serialize out (the finance list reads it) and be dropped on the
 * way in.
 */
class StudentFinanceNetAmountPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void netAmountIsSerializedOutForTheClient() throws Exception {
        StudentFinance finance = new StudentFinance();
        finance.setId(42);
        finance.setStudentFinRecievedAmount(5000);
        finance.setWafarAmount(1200);
        finance.setNetAmount(3800);

        String json = objectMapper.writeValueAsString(finance);

        assertTrue(json.contains("\"netAmount\":3800"),
                "the finance list reads netAmount off the row, so it must be serialized");
    }

    @Test
    void aForgedNetAmountIsIgnoredOnTheWayIn() throws Exception {
        StudentFinance parsed = objectMapper.readValue(
                "{\"id\":42,\"studentFinRecievedAmount\":5000,\"wafarAmount\":1200,\"netAmount\":999999}",
                StudentFinance.class);

        assertEquals(42, parsed.getId());
        assertEquals(5000, parsed.getStudentFinRecievedAmount());
        assertEquals(1200, parsed.getWafarAmount());
        assertNull(parsed.getNetAmount(),
                "netAmount is owned by the NET_AMOUNT computed column, never by the caller");
    }

    @Test
    void archivedNetAmountIsAlsoReadOnly() throws Exception {
        StudentFinanceArch parsed = objectMapper.readValue(
                "{\"studentId\":42,\"studentFinRecievedAmount\":5000,\"netAmount\":999999}",
                StudentFinanceArch.class);

        assertEquals(5000, parsed.getStudentFinRecievedAmount());
        assertNull(parsed.getNetAmount());
    }
}
