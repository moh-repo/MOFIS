package com.dilaw.mohe.controler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The travel-ticket collection URL: what the Travel Tickets page relies on.
 *
 * <p>It accepts a POST with and without the trailing slash (live QA H4, 24 Sep
 * 2026). The Add Travel Ticket dialog posts to {@code /api/v1/student-travel-tickets};
 * the controller mapped {@code POST /} only, so every ticket added from the
 * screen died with 405 while the API with the slash worked. {@code GET} already
 * accepted both spellings; the create now does the same, and this test keeps
 * the two in step.
 *
 * <p>Its GET takes the page's two filters, {@code studentId} and {@code travelType},
 * as optional query parameters (24 Sep 2026). The page had sent them all along;
 * the endpoint took none, so the filter bar changed nothing.
 */
class StudentTravelTicketEndpointsTest {

    private static Method handler(String name, Class<?> mapping) {
        return Arrays.stream(StudentTravelTicketController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name) && m.isAnnotationPresent((Class) mapping))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("POST create maps both '' and '/' like GET getAll")
    void createAcceptsBothSpellings() {
        Method create = handler("create", PostMapping.class);
        Method getAll = handler("getAll", GetMapping.class);

        String[] postPaths = create.getAnnotation(PostMapping.class).value();
        String[] getPaths = getAll.getAnnotation(GetMapping.class).value();

        assertThat(postPaths).containsExactlyInAnyOrder("", "/");
        assertThat(postPaths).containsExactlyInAnyOrder(getPaths);
    }

    @Test
    @DisplayName("GET getAll takes studentId and travelType as optional query parameters")
    void getAllTakesThePageFilters() {
        Parameter[] params = handler("getAll", GetMapping.class).getParameters();
        assertThat(params).hasSize(2);

        RequestParam student = params[0].getAnnotation(RequestParam.class);
        assertThat(student).as("studentId is a query parameter").isNotNull();
        assertThat(student.name()).isEqualTo("studentId");
        assertThat(student.required()).isFalse();
        assertThat(params[0].getType()).isEqualTo(Integer.class);

        RequestParam type = params[1].getAnnotation(RequestParam.class);
        assertThat(type).as("travelType is a query parameter").isNotNull();
        assertThat(type.name()).isEqualTo("travelType");
        assertThat(type.required()).isFalse();
        assertThat(params[1].getType()).isEqualTo(String.class);
    }
}
