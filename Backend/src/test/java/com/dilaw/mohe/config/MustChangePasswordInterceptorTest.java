package com.dilaw.mohe.config;

import com.dilaw.mohe.ums.service.MustChangePasswordGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * An account holding a password an administrator set can do nothing but change it
 * (V121, DEC-04 — QA SEC-001).
 */
class MustChangePasswordInterceptorTest {

    /** Gated like any handler (ControllerAuthorizationArchTest); standalone MockMvc applies no method security. */
    @RestController
    @org.springframework.security.access.prepost.PreAuthorize("permitAll()")
    static class Endpoints {
        @GetMapping("/api/v1/studentdata/1") public String student() { return "student"; }
        @PostMapping("/api/v1/ums/auth/change-password") public String change() { return "changed"; }
        @GetMapping("/api/v1/ums/auth/me") public String me() { return "me"; }
    }

    private final MustChangePasswordGate gate = mock(MustChangePasswordGate.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MustChangePasswordInterceptor interceptor = new MustChangePasswordInterceptor(gate);
        mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
                .addInterceptors(new MappedInterceptor(new String[] {"/api/**"},
                        MustChangePasswordInterceptor.ALLOWED, interceptor))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("5", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("while the flag is set, ordinary endpoints answer 403 PASSWORD_CHANGE_REQUIRED")
    void blocksOrdinaryEndpoints() throws Exception {
        when(gate.mustChange(5L)).thenReturn(true);

        var res = mvc.perform(get("/api/v1/studentdata/1")).andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains(MustChangePasswordInterceptor.CODE);
    }

    @Test
    @DisplayName("the password change and /me stay open, or the account could never leave the state")
    void leavesTheWayOutOpen() throws Exception {
        when(gate.mustChange(5L)).thenReturn(true);

        assertThat(mvc.perform(post("/api/v1/ums/auth/change-password")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(mvc.perform(get("/api/v1/ums/auth/me")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("an account without the flag is not affected")
    void ordinaryAccountPasses() throws Exception {
        when(gate.mustChange(5L)).thenReturn(false);

        assertThat(mvc.perform(get("/api/v1/studentdata/1")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
