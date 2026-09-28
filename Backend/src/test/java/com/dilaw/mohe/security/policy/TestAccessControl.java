package com.dilaw.mohe.security.policy;

import org.mockito.Mockito;

import java.util.List;

/**
 * An {@link AccessControl} over real policies for unit tests that build a
 * service with {@code @InjectMocks}: the subject resolver is a lenient mock
 * answering {@link AccessSubject#anonymous()} unless the test stubs it.
 */
public final class TestAccessControl {

    private TestAccessControl() {}

    public static AccessSubjectResolver anonymousResolver() {
        AccessSubjectResolver resolver = Mockito.mock(AccessSubjectResolver.class);
        Mockito.lenient().when(resolver.current()).thenReturn(AccessSubject.anonymous());
        return resolver;
    }

    public static AccessControl of(AccessPolicy<?>... policies) {
        return new AccessControl(List.of(policies), anonymousResolver());
    }

    /** The same, with a fixed signed-in subject for every decision. */
    public static AccessControl as(AccessSubject subject, AccessPolicy<?>... policies) {
        AccessSubjectResolver resolver = Mockito.mock(AccessSubjectResolver.class);
        Mockito.lenient().when(resolver.current()).thenReturn(subject);
        return new AccessControl(List.of(policies), resolver);
    }
}
