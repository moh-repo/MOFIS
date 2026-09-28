package com.dilaw.mohe.scope;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a refusal looks like when a country officer reaches for another attaché's
 * record, and why it is not always the same status.
 *
 * <p>These tests were written against the application's own guard, which answers
 * {@code 403 FORBIDDEN}: it resolves the student, compares the study country with
 * the caller's scope, and refuses. That is still what happens whenever the guard
 * is the first thing to say no.
 *
 * <p>It is no longer always first. {@code tenant_country_policy} has been
 * {@code STATE = ON} since V062, and it binds every connection including
 * {@code sysadmin}. A Jordan-scoped connection asking for an Egyptian student does
 * not get a row it may not have — it gets no row at all, because the predicate
 * filtered it before the query returned. {@code StudentDataService.getStudentData}
 * then raises {@code 404 NOT_FOUND} for a record it genuinely cannot see, and the
 * country check it would have made never runs. Every student on the development
 * database studies in Egypt, so this is the path every one of these tests takes.
 *
 * <p>Both answers are correct refusals, and 404 is the better of the two: a 403
 * confirms that the named id exists, which is the whole of what an attacker
 * walking small sequential ids needs. {@code ApprovalWorkflowService.getRequestById}
 * already reasons this way in the production code and returns empty rather than
 * forbidden. So these assertions accept either, and lean on the positive control
 * beside them — the ministry reads the same id in the same test — to prove the
 * refusal is the scope talking and not an id that was never there.
 *
 * <p>What is <b>not</b> accepted is success, or any other status. A guard that
 * stopped running would return the record and fail the assertion.
 */
final class ScopeDenial {

    private ScopeDenial() {
    }

    /** The two ways a scoped read is refused: the guard's, and the policy's. */
    private static final List<HttpStatus> REFUSALS = List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

    /**
     * Asserts the call is refused for scope — by the application guard (403) or by
     * the row-level policy that filtered the row away first (404).
     *
     * @param what a description of the read, for the failure message
     */
    static void assertRefused(String what, ThrowingCallable call) {
        assertThatThrownBy(call)
                .as("%s from another attaché must be refused", what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> (HttpStatusCode) ((ResponseStatusException) e).getStatusCode())
                .as("""
                    %s: expected the scope to refuse this. 403 is the guard refusing a row it \
                    could see; 404 is tenant_country_policy having filtered the row away before \
                    the guard could look. Any other status, or no exception at all, means the \
                    scope did not refuse.""", what)
                .isIn(REFUSALS);
    }
}
