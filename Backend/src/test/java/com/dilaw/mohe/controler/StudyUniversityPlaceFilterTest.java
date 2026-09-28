package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudyUniversityIn;
import com.dilaw.mohe.service.StudyUniversityInService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Live QA M10 (25 Sep 2026): the Add Student wizard offered Egypt's universities for a
 * Jordan student. The list is now filtered by study country and governorate on the
 * server; the "Not Specified" row, which names no place, comes with every one. The rows
 * are the development database's three (read 26 Sep 2026).
 */
@ExtendWith(MockitoExtension.class)
class StudyUniversityPlaceFilterTest {

    private static final int EGYPT = 1;
    private static final int JORDAN = 1004;

    @Mock
    private StudyUniversityInService service;

    @InjectMocks
    private LookupTableController controller;

    private static StudyUniversityIn university(int id, int country, int gov) {
        StudyUniversityIn u = new StudyUniversityIn();
        u.setId(id);
        u.setStudyUniversityInName("u" + id);
        u.setStudyCountryInId(country);
        u.setStudyGovInId(gov);
        return u;
    }

    @BeforeEach
    void rows() {
        when(service.getAll()).thenReturn(List.of(
                university(1, EGYPT, 1),       // Cairo University
                university(2, EGYPT, 1),       // Ain Shams University
                university(999, 0, 0)));       // Not Specified
    }

    private List<Integer> ids(Integer country, Integer gov) {
        Map<String, Object> body = controller.getAllStudyUniversitiesIn(null, null, null, null, null, country, gov).getBody();
        return ((List<?>) body.get("content")).stream().map(u -> ((StudyUniversityIn) u).getId()).toList();
    }

    @Test
    @DisplayName("Jordan: no Egyptian university, only the row that names no place")
    void jordan() {
        assertThat(ids(JORDAN, null)).containsExactly(999);
    }

    @Test
    @DisplayName("Egypt: its two universities and Not Specified, before a governorate is chosen")
    void egyptWithoutGovernorate() {
        assertThat(ids(EGYPT, null)).containsExactlyInAnyOrder(1, 2, 999);
    }

    @Test
    @DisplayName("a governorate with no university narrows to Not Specified, not to every university")
    void governorateWithNone() {
        assertThat(ids(EGYPT, 2)).containsExactly(999);
    }

    @Test
    @DisplayName("no place asked (the Lookup Tables screen, the reports): every row")
    void unfiltered() {
        assertThat(ids(null, null)).containsExactlyInAnyOrder(1, 2, 999);
    }
}
