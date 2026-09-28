package com.dilaw.mohe.security.field;

import com.dilaw.mohe.config.FieldPermission;
import com.dilaw.mohe.config.OwnedByUser;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Field-level rules, both directions (security plan R-18, decision D-9 a).
 *
 * <p>Read: the catalogued {@code .VIEW} code decides when the administrators
 * have defined it, the annotation's {@code anyOf} otherwise. Write: a changed
 * field the caller may not edit is refused with 400, the whole request with it.
 */
class FieldAccessServiceTest {

    /** A record shaped like the entities: one catalogued field, one fallback-only, one owner-visible. */
    static class Row implements OwnedByUser {
        @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_EMAIL", anyOf = {"STUDENT_QUICK_EDIT.VIEW"})
        String email;
        @FieldPermission(anyOf = {"FINANCE.VIEW"}, editAnyOf = {"FINANCE.EDIT"})
        String account;
        @FieldPermission(page = "UMS_ADMIN", field = "USER_PHONE", ownerVisible = true)
        String phone;
        String name;
        Long owner;

        Row(String email, String account, String phone, String name, Long owner) {
            this.email = email; this.account = account; this.phone = phone; this.name = name; this.owner = owner;
        }

        @Override
        public Long ownerUserId() {
            return owner;
        }
    }

    private final UmsPermissionRepository repository = mock(UmsPermissionRepository.class);

    private FieldAccessService service(String... definedCodes) {
        when(repository.findActiveFieldPermissionCodes()).thenReturn(List.of(definedCodes));
        return new FieldAccessService(repository);
    }

    private static void signIn(long userId, String... codes) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, Set.of(codes).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static FieldAccessService.Rule rule(String field) {
        return FieldAccessService.rulesOf(Row.class).get(field);
    }

    @Test
    @DisplayName("without a catalogued code the anyOf fallback decides, as @SensitiveField always did")
    void fallbackRead() {
        FieldAccessService s = service();
        Row row = new Row("a@b", "123", "555", "n", 9L);
        signIn(1, "STUDENTS_LIST.VIEW");
        assertThat(s.mayView(rule("email"), row)).isFalse();
        assertThat(s.mayView(rule("account"), row)).isFalse();
        signIn(1, "STUDENT_QUICK_EDIT.VIEW", "FINANCE.VIEW");
        assertThat(s.mayView(rule("email"), row)).isTrue();
        assertThat(s.mayView(rule("account"), row)).isTrue();
    }

    @Test
    @DisplayName("once the administrators define the field code it decides, and anyOf no longer opens the field")
    void cataloguedRead() {
        FieldAccessService s = service("STUDENTS_LIST.STUDENT_EMAIL.VIEW");
        Row row = new Row("a@b", "123", "555", "n", 9L);
        signIn(1, "STUDENT_QUICK_EDIT.VIEW");
        assertThat(s.mayView(rule("email"), row)).as("anyOf held, but the catalogue rules now").isFalse();
        signIn(1, "STUDENTS_LIST.STUDENT_EMAIL.VIEW");
        assertThat(s.mayView(rule("email"), row)).isTrue();
    }

    @Test
    @DisplayName("an account always sees its own owner-visible fields; a field with no rule at all is visible")
    void ownerVisible() {
        FieldAccessService s = service("UMS_ADMIN.USER_PHONE.VIEW");
        Row mine = new Row("a@b", "123", "555", "n", 1L);
        Row theirs = new Row("a@b", "123", "555", "n", 2L);
        signIn(1);
        assertThat(s.mayView(rule("phone"), mine)).isTrue();
        assertThat(s.mayView(rule("phone"), theirs)).isFalse();
        FieldAccessService none = service();
        assertThat(none.mayView(rule("phone"), theirs)).as("no code defined, no anyOf: visible").isTrue();
    }

    @Test
    @DisplayName("D-9 a: a changed field the caller may not edit refuses the request with 400 and names the field")
    void writeRefused() {
        FieldAccessService s = service("STUDENTS_LIST.STUDENT_EMAIL.EDIT", "STUDENTS_LIST.STUDENT_EMAIL.VIEW");
        Row before = new Row("old@b", "123", "555", "n", 9L);
        Row after = new Row("new@b", "123", "555", "renamed", 9L);
        signIn(1, "STUDENTS_LIST.STUDENT_EMAIL.VIEW", "FINANCE.EDIT");

        assertThat(s.changedProperties(Row.class, before, after, false)).containsExactly("email");
        assertThatThrownBy(() -> s.assertMayEdit(Row.class, before, after, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("email");

        signIn(1, "STUDENTS_LIST.STUDENT_EMAIL.VIEW", "STUDENTS_LIST.STUDENT_EMAIL.EDIT");
        assertThatCode(() -> s.assertMayEdit(Row.class, before, after, false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("editAnyOf is the write fallback; an unchanged masked field is not a change; nulls on a partial patch are not submitted")
    void writeFallbackAndChangeDetection() {
        FieldAccessService s = service();
        Row before = new Row("a@b", "123", "555", "n", 9L);
        Row sameAccount = new Row("a@b", "123", null, "n2", 9L);
        Row newAccount = new Row("a@b", "999", null, "n2", 9L);
        signIn(1, "FINANCE.VIEW");
        assertThatCode(() -> s.assertMayEdit(Row.class, before, sameAccount, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> s.assertMayEdit(Row.class, before, newAccount, true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("account");
        // Clearing the phone on a full update is a change; on a partial patch it is "not sent".
        assertThat(s.changedProperties(Row.class, before, sameAccount, false)).containsExactly("phone");
        assertThat(s.changedProperties(Row.class, before, sameAccount, true)).isEmpty();
    }
}
