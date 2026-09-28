package com.dilaw.mohe.ums.controler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-02 / SEC-03: permissions, roles and groups are global, so every write to
 * them is ministry-only, as pages, reports and API links already were.
 *
 * <p>They were gated on {@code UMS_ADMIN.ADD/EDIT/DELETE}, which a country admin
 * holds to administer its own staff. A country admin could rename a permission it
 * held to {@code SYSTEM.RESTORE}, or rename or deactivate a global role. Each write
 * expression is evaluated here, as Spring Security would, for a country admin
 * (refused) and a ministry admin (allowed).
 */
class UmsCatalogueWritesAreMinistryOnlyTest {

    private static final List<String> UMS_ADMIN = List.of("UMS_ADMIN.VIEW", "UMS_ADMIN.ADD", "UMS_ADMIN.EDIT",
            "UMS_ADMIN.DELETE");

    private static List<Method> writeMethods(Class<?> controller) {
        List<Method> out = new ArrayList<>();
        for (Method m : controller.getDeclaredMethods()) {
            if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class)) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean allows(String expression, String tierAuthority) {
        List<String> authorities = new ArrayList<>(UMS_ADMIN);
        authorities.add(tierAuthority);
        var auth = new UsernamePasswordAuthenticationToken("7", null,
                AuthorityUtils.createAuthorityList(authorities.toArray(String[]::new)));
        SecurityExpressionRoot root = new SecurityExpressionRoot(auth) { };
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(expression)
                .getValue(new StandardEvaluationContext(root), Boolean.class));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {UmsPermissionController.class, UmsRoleController.class, UmsGroupController.class})
    @DisplayName("every write is refused to a country admin holding UMS_ADMIN.*, and allowed to a ministry admin")
    void writesAreMinistryOnly(Class<?> controller) {
        List<Method> writes = writeMethods(controller);
        assertThat(writes).as("the controller has write endpoints").isNotEmpty();
        for (Method m : writes) {
            PreAuthorize gate = m.getAnnotation(PreAuthorize.class);
            assertThat(gate).as(m.getName() + " carries its own gate").isNotNull();
            assertThat(allows(gate.value(), "ROLE_COUNTRY_ADMIN"))
                    .as(m.getName() + " refused to a country admin").isFalse();
            assertThat(allows(gate.value(), "ROLE_MINISTRY_ADMIN"))
                    .as(m.getName() + " allowed to a ministry admin").isTrue();
        }
    }
}
