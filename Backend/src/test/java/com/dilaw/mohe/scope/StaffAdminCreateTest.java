package com.dilaw.mohe.scope;

import com.dilaw.mohe.ums.model.UmsField;
import com.dilaw.mohe.ums.model.UmsPage;
import com.dilaw.mohe.ums.repository.UmsFieldRepository;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.UmsPageService;
import com.dilaw.mohe.ums.service.UmsUserService;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.config.tenant.TenantContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Add works on the two Staff-access screens where it could not.
 *
 * <p><b>Users.</b> {@code UmsUser.passwordHash} is {@code @JsonIgnore} on the
 * field, and that suppresses deserialization as well as serialization — so the
 * value the dialog posted as {@code passwordHash} was dropped, nothing was
 * hashed, and the insert met a NOT NULL column. The password now arrives as a
 * write-only {@code password} property that {@code createUser} always encodes;
 * a ready-made hash cannot be submitted at all, which the old "skip encoding if
 * it already looks like bcrypt" rule would have permitted.
 *
 * <p><b>Fields.</b> {@code UmsField.pageId} was a getter with no setter, which
 * makes it read-only to Jackson: the parent id was discarded, and generating the
 * field's permissions then dereferenced a null page — a 500 on every Add.
 *
 * <p>What is asserted is the part that was actually broken: that the stored hash
 * verifies against the password that was submitted, and that a created field
 * knows its page. Both rows are removed in a {@code finally}.
 */
@Tag("integration")
@SpringBootTest
class StaffAdminCreateTest {

    @Autowired private UmsUserService users;
    @Autowired private UmsUserRepository userRepository;
    @Autowired private UmsPageService pages;
    @Autowired private UmsFieldRepository fieldRepository;
    @Autowired private UmsPermissionRepository permissionRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;

    /**
     * The services build their own, so there is no bean to inject; a fresh one
     * verifies the same hashes, which is the whole point of a salted digest.
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static String marker() {
        return "sa-test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("a created staff account stores a hash of the password that was sent")
    void createUserStoresAUsableHash() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            String password = "Str0ng!" + UUID.randomUUID().toString().substring(0, 6);
            Long id = null;
            try {
                UmsUser draft = new UmsUser();
                draft.setUserName(marker());
                draft.setFirstName("Create");
                draft.setLastName("Test");
                draft.setEmail(marker() + "@example.test");
                draft.setStatus(UserStatus.ACTIVE);
                draft.setPassword(password);

                UmsUser created = users.createUser(draft, AsUser.SUPERADMIN);
                id = created.getId();
                assertThat(id).as("the account was created").isNotNull();

                UmsUser stored = userRepository.findById(id).orElseThrow();
                assertThat(stored.getPasswordHash())
                        .as("a hash was stored").isNotNull().isNotBlank();
                assertThat(passwordEncoder.matches(password, stored.getPasswordHash()))
                        .as("and it is a hash of the password that was submitted")
                        .isTrue();
                assertThat(stored.getPasswordHash())
                        .as("the clear password is not what was stored")
                        .isNotEqualTo(password);
                assertThat(stored.getMustChangePassword())
                        .as("DEC-04: a password the administrator chose must be changed at first sign-in")
                        .isTrue();
            } finally {
                if (id != null) { userRepository.deleteById(id); }
            }
        });
    }

    @Test
    @DisplayName("the holder's own change clears the must-change flag; an administrator's reset sets it again")
    void ownChangeClearsAndResetSetsTheFlag() {
        // Rolled back as a whole: a password change writes history and audit rows that
        // hold keys to the account, so it cannot simply be deleted afterwards.
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            AsUser.run(AsUser.SUPERADMIN, () -> {
                UmsUser draft = new UmsUser();
                draft.setUserName(marker());
                draft.setFirstName("Flag");
                draft.setLastName("Test");
                draft.setEmail(marker() + "@example.test");
                draft.setStatus(UserStatus.ACTIVE);
                draft.setPassword("Str0ng-" + UUID.randomUUID().toString().substring(0, 8));
                Long id = users.createUser(draft, AsUser.SUPERADMIN).getId();

                users.changePassword(id, "Own-" + UUID.randomUUID().toString().substring(0, 10) + "9", id);
                assertThat(userRepository.findById(id).orElseThrow().getMustChangePassword()).isFalse();

                users.changePassword(id, "Reset-" + UUID.randomUUID().toString().substring(0, 10) + "7",
                        AsUser.SUPERADMIN);
                assertThat(userRepository.findById(id).orElseThrow().getMustChangePassword()).isTrue();
            });
            return null;
        }));
    }

    @Test
    @DisplayName("a create with a password the policy refuses is refused (QA SEC-001: P@ssw0rd was accepted)")
    void createUserAppliesThePasswordPolicy() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            UmsUser draft = new UmsUser();
            draft.setUserName(marker());
            draft.setFirstName("Weak");
            draft.setLastName("Password");
            draft.setEmail(marker() + "@example.test");
            draft.setStatus(UserStatus.ACTIVE);
            draft.setPassword("P@ssw0rd");

            assertThatThrownBy(() -> users.createUser(draft, AsUser.SUPERADMIN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 12");
        });
    }

    @Test
    @DisplayName("a create without a last name is refused on the server, not only in the browser")
    void createUserRequiresBothNames() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            UmsUser draft = new UmsUser();
            draft.setUserName(marker());
            draft.setFirstName("Only");
            draft.setEmail(marker() + "@example.test");
            draft.setStatus(UserStatus.ACTIVE);
            draft.setPassword("Str0ng-" + UUID.randomUUID().toString().substring(0, 8));

            assertThatThrownBy(() -> users.createUser(draft, AsUser.SUPERADMIN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        });
    }

    @Test
    @DisplayName("a create with no password is refused rather than saved without one")
    void createUserWithoutAPasswordIsRefused() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            UmsUser draft = new UmsUser();
            draft.setUserName(marker());
            draft.setFirstName("No");
            draft.setLastName("Password");
            draft.setStatus(UserStatus.ACTIVE);

            assertThatThrownBy(() -> users.createUser(draft, AsUser.SUPERADMIN))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("a created field belongs to the page the request named")
    void createFieldResolvesItsPage() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            List<UmsPage> allPages = pages.getAllActivePages();
            assertThat(allPages).as("the seeded pages this test picks a parent from").isNotEmpty();
            UmsPage parent = allPages.get(0);

            Long fieldId = null;
            try {
                UmsField draft = new UmsField();
                draft.setFieldCode(marker().toUpperCase());
                // Unique per run: creating a field generates VIEW and EDIT
                // permissions named after it, and PermissionName is unique.
                draft.setFieldName("Create Test Field " + UUID.randomUUID());
                draft.setPageId(parent.getId());

                UmsField created = pages.createField(draft);
                fieldId = created.getId();

                assertThat(fieldId).as("the field was created").isNotNull();
                assertThat(created.getPageId())
                        .as("and it knows the page it was created under")
                        .isEqualTo(parent.getId());
            } finally {
                // Hard cleanup, not pages.deleteField: that only sets IsActive =
                // false and leaves the two generated permissions behind, which
                // would collide on the next run.
                if (fieldId != null) {
                    permissionRepository.deleteAll(
                            permissionRepository.findByResourceTypeAndResource_IdAndIsActiveTrue("FIELD", fieldId));
                    fieldRepository.deleteById(fieldId);
                }
            }
        });
    }

    @Test
    @DisplayName("a field created with no page is a 400, not a 500")
    void createFieldWithoutAPageIsRefused() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            UmsField draft = new UmsField();
            draft.setFieldCode(marker().toUpperCase());
            draft.setFieldName("Orphan");

            assertThatThrownBy(() -> pages.createField(draft))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        });
    }
}
