package com.dilaw.mohe.ums.model;

import com.dilaw.mohe.codes.UserStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "Users", schema = "UMSCORE")
public class UmsUser implements com.dilaw.mohe.config.OwnedByUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    // SEC-01: the id is written by the database and the path, never by a request body.
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String userName;

    /** R-18: masked when the administrators define UMS_ADMIN.USER_EMAIL.VIEW; the account itself always sees it. */
    @com.dilaw.mohe.config.FieldPermission(page = "UMS_ADMIN", field = "USER_EMAIL", ownerVisible = true)
    @Column(name = "Email", length = 255)
    private String email;

    @com.dilaw.mohe.config.FieldPermission(page = "UMS_ADMIN", field = "USER_PHONE", ownerVisible = true)
    @Column(name = "Phone", length = 50)
    private String phone;

    @Column(name = "PasswordHash", nullable = false, length = 255)
    @JsonIgnore
    private String passwordHash;

    /**
     * The password a create request states, in clear, for hashing.
     *
     * <p>Not a column. {@code passwordHash} is {@code @JsonIgnore} on the field,
     * which suppresses <em>deserialization</em> as well as serialization, so the
     * value the Add dialog posted as {@code passwordHash} never bound: the account
     * was saved with no hash against a NOT NULL column and creating a staff user
     * failed every time. This is the property the client sends instead — write
     * only, so it is never echoed back — and {@code createUser} hashes it and
     * clears it. A caller cannot hand over a ready-made hash this way, which the
     * old "encode unless it already looks like bcrypt" rule would have allowed.
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "FirstName", length = 100)
    private String firstName;

    @Column(name = "LastName", length = 100)
    private String lastName;

    @Column(name = "Status", nullable = false, length = 20)
    private String status = UserStatus.ACTIVE; // codes of LU_USER_STATUS

    @Column(name = "IsAdmin", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("isAdmin")
    private Boolean isAdmin = false;

    @Column(name = "IsSuperAdmin", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("isSuperAdmin")
    private Boolean isSuperAdmin = false;

    @Column(name = "IsMinistryUser", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("isMinistryUser")
    private Boolean isMinistryUser = false;

    /**
     * Failed sign-ins since the last success or lock — the count within the
     * current rung of {@code MOHECORE.AUTH_LOCKOUT_POLICY}, not a lifetime total.
     */
    @Column(name = "FailedLoginAttempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    /**
     * How many locks this run of failures has already earned, so which rung of
     * the ladder applies next: 0 is the first stage. Reset by a successful
     * sign-in and by an administrator unlocking the account (V080).
     */
    @Column(name = "LockoutStage", nullable = false)
    private Integer lockoutStage = 0;

    /**
     * When a timed lock ends. {@code null} with {@code Status = 'LOCKED'} is the
     * ladder's last rung (and the administrator's manual lock): only an
     * administrator opens it.
     */
    @Column(name = "LockedUntil")
    private LocalDateTime lockedUntil;

    /** When the most recent failure happened, so a stale run can be forgotten. */
    @Column(name = "LastFailedLoginAt")
    private LocalDateTime lastFailedLoginAt;

    @Column(name = "LastLoginAt")
    private LocalDateTime lastLoginAt;

    @Column(name = "PasswordChangedAt")
    private LocalDateTime passwordChangedAt;

    /**
     * V121 (DEC-04): the password was set by an administrator — at creation or by a
     * reset — and the holder must replace it before doing anything else. Read-only
     * to clients: only {@code UmsUserService} sets or clears it.
     */
    @Column(name = "MustChangePassword", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean mustChangePassword = false;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "CreatedBy")
    private Long createdBy;

    @Column(name = "UpdatedBy")
    private Long updatedBy;

    @Column(name = "IsDeleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * The position this account holds (a {@code MOHECORE.LU_POSITION} code, V093).
     *
     * <p>Security plan N-03: an approval level may require a position, resolved
     * per country at approval time, so the position has to be an attribute of
     * the account rather than free text on the level. Null until an
     * administrator sets it; the effective-permission report flags approvers
     * without one.
     */
    @Column(name = "PositionCode", length = 50)
    private String positionCode;

    // Relationships
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UmsUserGroup> userGroups = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UmsUserRole> userRoles = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UmsUserCountry> userCountries = new HashSet<>();

    /**
     * The attachés this account covers, for display only.
     *
     * <p>Not mapped, and filled in by {@code UmsUserService} from one batched
     * query per page. The association above stays {@code @JsonIgnore} and lazy on
     * purpose: serialising it would drag {@code UmsUserCountry} and its back
     * reference into every response, and touching it outside a session is the
     * lazy-initialisation failure this avoids.
     *
     * <p>The staff list needs it because country scope is otherwise invisible on
     * screen — rows are simply absent, with nothing saying why, and an account
     * awaiting assignment looks exactly like a fully assigned one.
     */
    @Transient
    private List<Integer> countryIds = new ArrayList<>();

    @Transient
    private List<String> countryNames = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper method to get full name
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null)
            sb.append(firstName);
        if (lastName != null) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(lastName);
        }
        return sb.toString();
    }

    /** S-03: set on the response when an edit raised a privilege-change request instead of applying it. */
    @Transient
    private Long pendingApprovalRequestId;

    /** {@link com.dilaw.mohe.config.OwnedByUser}: an account always sees its own masked fields. */
    @Override
    @JsonIgnore
    public Long ownerUserId() {
        return getId();
    }
}
