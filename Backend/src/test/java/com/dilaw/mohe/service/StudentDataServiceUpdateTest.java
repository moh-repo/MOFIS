package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What the approval executor's update writes (live QA H3, 23 Sep 2026): the wizard's
 * e-mail edit was approved and then dropped, because the four contact columns were
 * never copied onto the stored row.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentDataServiceUpdateTest {

    @Mock private StudentDataRepository studentDataRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private StudentDocumentCountryMigrationService studentDocumentCountryMigrationService;
    @Mock private SysParameterService sysParameters;
    @Mock private com.dilaw.mohe.service.codelist.CodeListService codeLists;
    @InjectMocks private StudentDataService service;

    private static StudentDatum student(String email) {
        StudentDatum s = new StudentDatum();
        s.setStudentId(1003);
        s.setStudentName("QA-TEST Student");
        s.setStudyCountryIn(1);
        s.setStudentStudyStatusId(1);
        s.setStudentEmail(email);
        return s;
    }

    @Test
    @DisplayName("the contact fields reach the stored row")
    void copiesTheContactFields() {
        StudentDatum stored = student("old@example.org");
        stored.setStudentCellphone("0700000000");
        when(studentDataRepository.findById(1003)).thenReturn(Optional.of(stored));
        when(studentDataRepository.save(any(StudentDatum.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDatum proposed = student("new@example.org");
        proposed.setStudentCellphone("0777000000");
        proposed.setStudentHomeNumber("01234567");
        proposed.setStudentEmergencyContact("0799000000");

        StudentDatum saved = service.update(1003, proposed);

        assertThat(saved.getStudentEmail()).as("the field the probe changed").isEqualTo("new@example.org");
        assertThat(saved.getStudentCellphone()).isEqualTo("0777000000");
        assertThat(saved.getStudentHomeNumber()).isEqualTo("01234567");
        assertThat(saved.getStudentEmergencyContact()).isEqualTo("0799000000");
        assertThat(saved.getStudentId()).isEqualTo(1003);
    }
}
