package com.dilaw.mohe.ums.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The security code, now that the server owns it.
 *
 * <p>The old one was drawn by the login component and compared in the same
 * file, so its answer was a value in the browser and a request made anywhere
 * else never met it at all. These tests are about the properties that made
 * moving it worth doing: an id is good once, it does not outlive its window,
 * and nothing about the answer is handed to the caller.
 */
class LoginCaptchaServiceTest {

    private final LoginCaptchaService service = new LoginCaptchaService(3);

    @Test
    @DisplayName("what is issued is a real PNG, and the answer is not part of it")
    void issuesAnImageAndKeepsTheAnswer() throws Exception {
        LoginCaptchaService.IssuedCaptcha issued = service.issue();

        assertThat(issued.id()).isNotBlank();
        assertThat(ImageIO.read(new ByteArrayInputStream(issued.png())))
                .as("decodes as an image").isNotNull();
        // The record carries an id and pixels — there is no field an ambitious
        // client could read the answer out of.
        assertThat(LoginCaptchaService.IssuedCaptcha.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactlyInAnyOrder("id", "png");
    }

    @Test
    @DisplayName("a wrong answer is refused, and spends the challenge")
    void aWrongAnswerBurnsTheChallenge() {
        LoginCaptchaService.IssuedCaptcha issued = service.issue();
        String answer = solutionOf(issued);

        assertThat(service.solve(issued.id(), "WRONG")).isFalse();
        // Even the right answer will not help now: one image, one guess, so a
        // single challenge cannot be walked through an alphabet.
        assertThat(service.solve(issued.id(), answer)).isFalse();
    }

    @Test
    @DisplayName("the right answer is accepted once and only once")
    void theRightAnswerWorksOnce() {
        LoginCaptchaService.IssuedCaptcha issued = service.issue();
        String answer = solutionOf(issued);

        assertThat(service.solve(issued.id(), answer)).isTrue();
        assertThat(service.solve(issued.id(), answer)).as("replay").isFalse();
    }

    @Test
    @DisplayName("case does not matter, but an unknown or empty id does")
    void answersAreCaseInsensitiveAndIdsMustExist() {
        LoginCaptchaService.IssuedCaptcha issued = service.issue();
        assertThat(service.solve(issued.id(), solutionOf(issued).toLowerCase())).isTrue();

        assertThat(service.solve("not-an-id", "ABCDE")).isFalse();
        assertThat(service.solve(null, "ABCDE")).isFalse();
        assertThat(service.solve(issued.id(), null)).isFalse();
        assertThat(service.solve(issued.id(), "  ")).isFalse();
    }

    @Test
    @DisplayName("a challenge that has run out of time is refused")
    void anExpiredChallengeIsRefused() {
        LoginCaptchaService expiring = new LoginCaptchaService(0);
        LoginCaptchaService.IssuedCaptcha issued = expiring.issue();

        assertThat(expiring.solve(issued.id(), solutionOf(expiring, issued))).isFalse();
    }

    @Test
    @DisplayName("ids are not guessable from one another")
    void idsAreDistinct() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ids.add(service.issue().id());
        }
        assertThat(ids).hasSize(200);
    }

    /**
     * The answer the service is holding, read the only way a test can: by trying
     * the alphabet until one is accepted would spend the challenge, so instead
     * the store is read directly. Nothing outside this test can do the same —
     * that is the point of the class.
     */
    private String solutionOf(LoginCaptchaService.IssuedCaptcha issued) {
        return solutionOf(service, issued);
    }

    @SuppressWarnings("unchecked")
    private static String solutionOf(LoginCaptchaService service, LoginCaptchaService.IssuedCaptcha issued) {
        try {
            var field = LoginCaptchaService.class.getDeclaredField("outstanding");
            field.setAccessible(true);
            Object challenge = ((java.util.Map<String, Object>) field.get(service)).get(issued.id());
            var text = challenge.getClass().getDeclaredMethod("text");
            text.setAccessible(true);
            return (String) text.invoke(challenge);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
