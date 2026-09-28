package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudyFinReceivedStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The portal's Finance page says "received" only for a status that means it.
 *
 * <p>It used to treat any status id but 0 as received, so a student whose finance
 * status was "Not received" (2) was told the money had arrived (live QA #10).
 */
class PortalFinanceStatusTest {

    private static StudyFinReceivedStatus status(String code, String ar, String en) {
        StudyFinReceivedStatus s = new StudyFinReceivedStatus();
        s.setCode(code);
        s.setStudyFinReceivedStatusName(ar);
        s.setStudyFinReceivedStatusNameEn(en);
        return s;
    }

    private static PortalMyDataDto.Finance described(StudyFinReceivedStatus s) {
        PortalMyDataDto.Finance f = new PortalMyDataDto.Finance();
        f.describeStatus(s);
        return f;
    }

    @Test
    @DisplayName("RECEIVED and DISBURSED are received")
    void receivedCodes() {
        assertThat(described(status("RECEIVED", "تم الاستلام", "Received")).getReceived()).isTrue();
        assertThat(described(status("DISBURSED", "تم الصرف", "Disbursed")).getReceived()).isTrue();
    }

    @Test
    @DisplayName("NOT_RECEIVED, ON_HOLD and no status at all are not")
    void otherCodes() {
        PortalMyDataDto.Finance notReceived = described(status("NOT_RECEIVED", "لم يستلم", "Not Received"));
        assertThat(notReceived.getReceived()).isFalse();
        assertThat(notReceived.getStudyFinRecievedStatusNameEn()).isEqualTo("Not Received");

        assertThat(described(status("ON_HOLD", "معلق", "On Hold")).getReceived()).isFalse();
        assertThat(described(null).getReceived()).isFalse();
    }
}
