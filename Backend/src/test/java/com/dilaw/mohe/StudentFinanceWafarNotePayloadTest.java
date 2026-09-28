package com.dilaw.mohe;

import com.dilaw.mohe.model.StudentFinance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wafar note has no STUDENT_FINANCE column — it belongs to the wafar record,
 * which is only written once the finance request is approved. It therefore has to
 * survive the approval round trip:
 *
 *   ApprovalIntegrationService  -> objectMapper.writeValueAsString(proposedData)
 *   ApprovalRequest.requestData -> stored
 *   executeStudentFinanceRequest -> objectMapper.readValue(..., StudentFinance.class)
 *
 * If @Transient ever became @JsonIgnore, or the field were dropped, the note would
 * silently vanish between submitting and approving and no other test would notice.
 */
class StudentFinanceWafarNotePayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void wafarNoteSurvivesTheApprovalRoundTrip() throws Exception {
        StudentFinance proposed = new StudentFinance();
        proposed.setId(42);
        proposed.setWafarAmount(1200);
        proposed.setWafarTypeId(3);
        proposed.setWafarNote("Quarterly wafar deduction");

        String requestData = objectMapper.writeValueAsString(proposed);
        assertTrue(requestData.contains("wafarNote"),
                "wafarNote must be serialized into the approval payload");

        StudentFinance executed = objectMapper.readValue(requestData, StudentFinance.class);

        assertEquals("Quarterly wafar deduction", executed.getWafarNote());
        assertEquals(1200, executed.getWafarAmount());
        assertEquals(3, executed.getWafarTypeId());
    }

    @Test
    void payloadWithoutAWafarNoteDeserializesCleanly() throws Exception {
        StudentFinance executed = objectMapper.readValue(
                "{\"id\":42,\"wafarAmount\":0}", StudentFinance.class);

        assertEquals(42, executed.getId());
        assertEquals(null, executed.getWafarNote());
    }
}
