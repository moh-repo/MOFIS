package com.dilaw.mohe.config;

import com.dilaw.mohe.service.AlertStreamTicketService;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import com.dilaw.mohe.ums.service.UserAuthorityCache;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Nothing the browser says about itself decides what it may do.
 *
 * <p>This exists because of a question that is asked every time somebody opens
 * DevTools on this application and sees `umsPermissions`, `umsCountryIds`,
 * `umsIsMinistryUser` and `umsAdminTier` sitting in `localStorage` in plain
 * text: <em>can a user not simply edit those and become an administrator?</em>
 *
 * <p>The answer is no, and the reason is {@link JwtAuthFilter}: the only thing
 * it reads out of the caller's token is the user id and the session id. The
 * permission codes and the tier come from {@link UserAuthorityCache}, which
 * reads {@code UMSCORE.Users}, {@code UserRoles} and {@code RolePermissions} on
 * the server. The permission list in the browser is what draws the menu; it has
 * never been what a request is authorised against, and the two are not even the
 * same values in transit — the browser's copy is never sent back.
 *
 * <p>That is an easy property to lose by accident. Someone optimising a
 * database round-trip out of the request path could put the codes into the JWT
 * as claims, and every one of these tests would still pass by name while the
 * system quietly started trusting a value the client holds. So each test forges
 * the claim an attacker would forge, signed with the server's own key so the
 * signature is genuinely valid, and asserts that the authorities granted are
 * exactly the ones the database supplied.
 *
 * <p>See also {@code Backend/docs/THREAT_MODEL.md} T-15 and O-07 in the
 * security plan, which is where the decision to keep the token in
 * `localStorage` is recorded along with the controls that make it acceptable.
 */
class ClientSuppliedAuthorityIsIgnoredTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-thirty-two-bytes-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long USER_ID = 42L;

    /** What the database says this account is: one report, and no tier at all. */
    private static final List<String> REAL_CODES = List.of("REP_STATISTICAL.VIEW");

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 3600);
    private final UserAuthorityCache authorityCache = mock(UserAuthorityCache.class);
    private final UmsUserSessionService sessionService = mock(UmsUserSessionService.class);
    private final AlertStreamTicketService ticketService = new AlertStreamTicketService();

    private JwtAuthFilter filter() {
        when(sessionService.findActive(anyLong(), anyString()))
                .thenReturn(Optional.of(new com.dilaw.mohe.ums.model.UmsUserSession()));
        when(authorityCache.snapshot(anyLong())).thenAnswer(inv -> new UserAuthorityCache.Snapshot(
                inv.getArgument(0), false, REAL_CODES, Set.of(), AdminTier.COUNTRY_STAFF, null));
        return new JwtAuthFilter(jwtTokenService, authorityCache, sessionService, ticketService, true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * A token signed with the server's key, carrying whatever extra claims the
     * caller wants to assert about themselves. Signed properly on purpose: a
     * forgery that failed the signature check would prove nothing about whether
     * the claims are trusted, because it would never be parsed at all.
     */
    private static String tokenClaiming(java.util.Map<String, Object> forgedClaims) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject("attacker")
                .claim("userId", USER_ID)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L));
        forgedClaims.forEach(builder::claim);
        return builder.signWith(KEY).compact();
    }

    private Authentication authenticateWith(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/studentdata");
        request.setContextPath("/mohe");
        request.setServletPath("/api/v1/studentdata");
        request.addHeader("Authorization", "Bearer " + token);
        filter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static List<String> authoritiesOf(Authentication auth) {
        assertThat(auth).as("the token authenticated").isNotNull();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    @Test
    @DisplayName("permission codes forged into the token are ignored; the database's are used")
    void forgedPermissionClaimsGrantNothing() throws Exception {
        Authentication auth = authenticateWith(tokenClaiming(java.util.Map.of(
                "permissions", List.of("SYS_ADMIN.EDIT", "FIN_ARCHIVE.EXPORT", "UMS_GROUPS.DELETE"),
                "authorities", List.of("SYS_ADMIN.EDIT"),
                "scope", "SYS_ADMIN.EDIT")));

        assertThat(authoritiesOf(auth))
                .as("exactly what UserAuthorityCache returned, and nothing the caller asked for")
                .containsExactlyElementsOf(REAL_CODES);
    }

    @Test
    @DisplayName("an admin tier forged into the token confers no role authority")
    void forgedTierClaimGrantsNoRole() throws Exception {
        Authentication auth = authenticateWith(tokenClaiming(java.util.Map.of(
                "adminTier", "SUPER_ADMIN",
                "isAdmin", true,
                "isSuperAdmin", true,
                "isMinistryUser", true,
                "roles", List.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"))));

        // COUNTRY_STAFF is the one tier that adds no authority, so the whole list
        // is the permission codes — any ROLE_ here would have come from the claim.
        assertThat(authoritiesOf(auth))
                .as("no tier authority is granted on the caller's say-so")
                .containsExactlyElementsOf(REAL_CODES)
                .noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("country ids forged into the token do not reach the security context")
    void forgedCountryClaimsGrantNothing() throws Exception {
        Authentication auth = authenticateWith(tokenClaiming(java.util.Map.of(
                "countryIds", List.of(1, 999, 1000, 1001),
                "isMinistryUser", true)));

        assertThat(authoritiesOf(auth)).containsExactlyElementsOf(REAL_CODES);
        // Country reach is resolved by CountryScopeService from UMSCORE.UserCountries
        // against this id — the id is the only thing the token is allowed to say.
        assertThat(auth.getName()).isEqualTo(String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("the deleted flag comes from the database, not from the token")
    void databaseStillDecidesWhetherTheAccountExists() throws Exception {
        when(sessionService.findActive(anyLong(), anyString()))
                .thenReturn(Optional.of(new com.dilaw.mohe.ums.model.UmsUserSession()));
        when(authorityCache.snapshot(anyLong())).thenAnswer(inv -> new UserAuthorityCache.Snapshot(
                inv.getArgument(0), true, List.of(), Set.of(), AdminTier.COUNTRY_STAFF, null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/studentdata");
        request.setContextPath("/mohe");
        request.setServletPath("/api/v1/studentdata");
        request.addHeader("Authorization", "Bearer " + tokenClaiming(java.util.Map.of("isSuperAdmin", true)));

        new JwtAuthFilter(jwtTokenService, authorityCache, sessionService, ticketService, true)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("a deleted account authenticates nothing, whatever its token claims")
                .isNull();
    }
}
