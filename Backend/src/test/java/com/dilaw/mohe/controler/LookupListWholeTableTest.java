package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.service.StudyCountryInService;
import com.dilaw.mohe.util.LookupTablePageUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Live QA M7 / M8 (25 Sep 2026): report country filters showed 10 of 16 study countries,
 * and Modify & approve offered 10 of 21 current levels. Twenty-four lookup lists defaulted
 * to a page of ten rows, and those callers ask for no page. Asking for no page now returns
 * the whole table in the same shape; the Lookup Tables screen, which always sends page and
 * size, pages as before.
 */
@ExtendWith(MockitoExtension.class)
class LookupListWholeTableTest {

    @Mock
    private StudyCountryInService studyCountryInService;

    @InjectMocks
    private LookupTableController controller;

    private static List<StudyCountryIn> countries(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> {
            StudyCountryIn c = new StudyCountryIn();
            c.setId(i);
            c.setStudyCountryInName("country " + i);
            return c;
        }).toList();
    }

    @Test
    @DisplayName("no page asked: all 16 study countries, in the page shape")
    void wholeTableWhenNoPage() {
        when(studyCountryInService.getAll()).thenReturn(countries(16));

        Map<String, Object> body = controller.getAllStudyCountriesIn(null, null, null, null, null).getBody();

        assertThat((List<?>) body.get("content")).hasSize(16);
        assertThat(body).containsEntry("totalElements", 16).containsEntry("totalPages", 1);
    }

    @Test
    @DisplayName("the Lookup Tables screen's page of ten is unchanged")
    void screenStillPages() {
        when(studyCountryInService.getAll()).thenReturn(countries(16));

        Map<String, Object> body = controller.getAllStudyCountriesIn(1, 10, null, null, null).getBody();

        assertThat((List<?>) body.get("content")).hasSize(6);
        assertThat(body).containsEntry("totalElements", 16).containsEntry("totalPages", 2);
    }

    @Test
    @DisplayName("the whole table is not cut at the 500-row page cap")
    void wholeIsNotCapped() {
        List<Integer> rows = IntStream.rangeClosed(1, 600).boxed().toList();
        assertThat((List<?>) LookupTablePageUtil.whole(rows, null, null, null).get("content")).hasSize(600);
    }

    @Test
    @DisplayName("no lookup list endpoint defaults to a page of ten any more")
    void noListDefaultsToTen() {
        List<Method> lists = Arrays.stream(LookupTableController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .filter(m -> Arrays.stream(m.getParameters()).anyMatch(p -> "size".equals(requestParamName(p))))
                .toList();
        assertThat(lists).hasSizeGreaterThanOrEqualTo(24);
        for (Method m : lists) {
            for (Parameter p : m.getParameters()) {
                if (!List.of("page", "size").contains(requestParamName(p))) continue;
                RequestParam rp = p.getAnnotation(RequestParam.class);
                assertThat(p.getType()).as("%s %s", m.getName(), p.getName()).isEqualTo(Integer.class);
                assertThat(rp.required()).as("%s %s", m.getName(), p.getName()).isFalse();
                assertThat(rp.defaultValue()).as("%s %s", m.getName(), p.getName()).isEqualTo(ValueConstants.DEFAULT_NONE);
            }
        }
    }

    private static String requestParamName(Parameter p) {
        RequestParam rp = p.getAnnotation(RequestParam.class);
        if (rp == null) return null;
        String name = !rp.value().isEmpty() ? rp.value() : rp.name();
        return name.isEmpty() ? p.getName() : name;
    }
}
