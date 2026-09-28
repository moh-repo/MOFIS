package com.dilaw.mohe.studentportal;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The portal security code: an id is good once, it does not outlive its window,
 * nothing about the answer is handed to the caller, and issuing one is budgeted
 * per address.
 *
 * <p>That last property is the one this class has and the staff service does not.
 * Issuing is an anonymous GET that allocates server-side state, and
 * {@code LoginRateLimitFilter} counts only POSTs on the portal prefix, so without
 * a budget here a loop on {@code /auth/captcha} would be free.
 */
class StudentPortalCaptchaServiceTest {

    private static final int TTL_MINUTES = 3;
    private static final int MAX_PER_MINUTE = 20;

    private final StudentPortalCaptchaService service =
            new StudentPortalCaptchaService(TTL_MINUTES, MAX_PER_MINUTE);

    @Test
    @DisplayName("what is issued is a real PNG, and the answer is not part of it")
    void issuesAnImageAndKeepsTheAnswer() throws Exception {
        StudentPortalCaptchaService.IssuedCaptcha issued = service.issue("10.0.0.1");

        assertThat(issued.id()).isNotBlank();
        assertThat(ImageIO.read(new ByteArrayInputStream(issued.png())))
                .as("decodes as an image").isNotNull();
        // The record carries an id and pixels — there is no field an ambitious
        // client could read the answer out of.
        assertThat(StudentPortalCaptchaService.IssuedCaptcha.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactlyInAnyOrder("id", "png");
    }

    @Test
    @DisplayName("a wrong answer is refused, and spends the challenge")
    void aWrongAnswerBurnsTheChallenge() {
        StudentPortalCaptchaService.IssuedCaptcha issued = service.issue("10.0.0.1");
        String answer = solutionOf(service, issued);

        assertThat(service.solve(issued.id(), "WRONG")).isFalse();
        // Even the right answer will not help now: one image, one guess, so a
        // single challenge cannot be walked through an alphabet.
        assertThat(service.solve(issued.id(), answer)).isFalse();
    }

    @Test
    @DisplayName("the right answer is accepted once and only once")
    void theRightAnswerWorksOnce() {
        StudentPortalCaptchaService.IssuedCaptcha issued = service.issue("10.0.0.1");
        String answer = solutionOf(service, issued);

        assertThat(service.solve(issued.id(), answer)).isTrue();
        assertThat(service.solve(issued.id(), answer)).as("replay").isFalse();
    }

    @Test
    @DisplayName("case does not matter, but an unknown or empty id does")
    void answersAreCaseInsensitiveAndIdsMustExist() {
        StudentPortalCaptchaService.IssuedCaptcha issued = service.issue("10.0.0.1");
        assertThat(service.solve(issued.id(), solutionOf(service, issued).toLowerCase())).isTrue();

        assertThat(service.solve("not-an-id", "ABCDE")).isFalse();
        assertThat(service.solve(null, "ABCDE")).isFalse();
        assertThat(service.solve(issued.id(), null)).isFalse();
        assertThat(service.solve(issued.id(), "  ")).isFalse();
    }

    @Test
    @DisplayName("a challenge that has run out of time is refused")
    void anExpiredChallengeIsRefused() {
        // A zero TTL puts expiresAt at the issuing instant. The entry is still in
        // the cache — retention is floored at a minute for exactly this reason —
        // so what refuses it is solve()'s own boundary check, which is the point:
        // "valid until T" has to mean invalid at T, not one instant after it.
        StudentPortalCaptchaService expiring = new StudentPortalCaptchaService(0, MAX_PER_MINUTE);
        StudentPortalCaptchaService.IssuedCaptcha issued = expiring.issue("10.0.0.1");

        assertThat(expiring.solve(issued.id(), solutionOf(expiring, issued))).isFalse();
    }

    @Test
    @DisplayName("ids are not guessable from one another")
    void idsAreDistinct() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < MAX_PER_MINUTE; i++) {
            ids.add(service.issue("10.0.0." + i).id());
        }
        assertThat(ids).hasSize(MAX_PER_MINUTE);
    }

    @Test
    @DisplayName("an address that keeps asking for images is cut off, and only that address")
    void issuingIsBudgetedPerAddress() {
        for (int i = 0; i < MAX_PER_MINUTE; i++) {
            assertThat(service.issue("10.0.0.1")).as("within budget").isNotNull();
        }

        assertThatThrownBy(() -> service.issue("10.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");

        // One address spending its budget must not cost a student behind a
        // different one their sign-in.
        assertThat(service.issue("10.0.0.2")).isNotNull();
    }

    @Test
    @DisplayName("callers with no resolvable address share one budget rather than escaping it")
    void anUnknownAddressStillHasABudget() {
        for (int i = 0; i < MAX_PER_MINUTE; i++) {
            service.issue(null);
        }
        assertThatThrownBy(() -> service.issue("   "))
                .as("blank and null land in the same bucket")
                .isInstanceOf(ResponseStatusException.class);
    }

    /**
     * The answer the service is holding, read the only way a test can: trying the
     * alphabet until one is accepted would spend the challenge, so the store is
     * read directly instead. Nothing outside this test can do the same — that is
     * the point of the class.
     */
    @SuppressWarnings("unchecked")
    private static String solutionOf(StudentPortalCaptchaService service,
                                     StudentPortalCaptchaService.IssuedCaptcha issued) {
        try {
            var field = StudentPortalCaptchaService.class.getDeclaredField("outstanding");
            field.setAccessible(true);
            Object challenge = ((Cache<String, Object>) field.get(service)).asMap().get(issued.id());
            var text = challenge.getClass().getDeclaredMethod("text");
            text.setAccessible(true);
            return (String) text.invoke(challenge);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
