package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The fail-closed guard on {@code cors.allow-dev-patterns}.
 *
 * <p>It used to fire only under the literal {@code prod} profile, so a process
 * started as {@code staging} (or a typo of prod) came up quietly trusting
 * {@code http://localhost:*} as a credentialed CORS origin. Now anything that is
 * not the {@code dev} profile refuses to start with the flag on.
 */
class WebSecurityConfigGuardTest {

    private static WebSecurityConfig config(boolean devProfile, boolean allowDevPatterns) {
        Environment env = mock(Environment.class);
        when(env.matchesProfiles("dev")).thenReturn(devProfile);
        WebSecurityConfig config = new WebSecurityConfig(env);
        ReflectionTestUtils.setField(config, "allowDevPatterns", allowDevPatterns);
        return config;
    }

    @Test
    @DisplayName("dev patterns outside the dev profile refuse to start")
    void nonDevWithPatternsFails() {
        assertThatThrownBy(() -> config(false, true).rejectDevPatternsOutsideDev())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors.allow-dev-patterns");
    }

    @Test
    @DisplayName("the dev profile may keep them; any profile without the flag starts fine")
    void permittedCombinations() {
        assertThatCode(() -> config(true, true).rejectDevPatternsOutsideDev()).doesNotThrowAnyException();
        assertThatCode(() -> config(false, false).rejectDevPatternsOutsideDev()).doesNotThrowAnyException();
        assertThatCode(() -> config(true, false).rejectDevPatternsOutsideDev()).doesNotThrowAnyException();
    }
}
