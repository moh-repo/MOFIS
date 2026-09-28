# Staff authentication: login flow and JWT claims

How a ministry staff account signs in, what the bearer token carries, and what the
server checks on every request. Written from the code; every statement names the
file it comes from. Paths below are servlet paths; the application runs under the
context path `/mohe` (`Backend/src/main/java/com/dilaw/mohe/config/SecurityConfig.java`),
so the browser calls `/mohe/api/v1/ums/auth/...`.

The student portal uses a different credential. It is described briefly at the end.

## Endpoints

All are in `Backend/src/main/java/com/dilaw/mohe/ums/controler/UmsAuthController.java`
under `api/v1/ums/auth`. `SecurityConfig` leaves `/api/v1/ums/auth/**` open (`permitAll`).
Endpoints that need a signed-in user check that themselves through `SecurityRequestUtils`.

| Method and path | Purpose |
|---|---|
| `GET /captcha` | Returns a PNG challenge. Its id comes back in the `X-Captcha-Id` header, and the answer stays on the server (`LoginCaptchaService`). |
| `POST /login` | Body `{username, password, captchaId, captchaAnswer}`. The key `userName` is also accepted. The CAPTCHA is checked first and is used up whether or not it was answered correctly. |
| `POST /mfa/verify` | Body `{mfaToken, code}`. Completes a sign-in that is waiting for the second factor. |
| `POST /mfa/enrol/start` | Returns `{secret, otpauthUri, qrPng}`. Can be called during sign-in (with `mfaToken`) or from the profile page (with the session). |
| `POST /mfa/enrol/confirm` | Body `{code, mfaToken?}`. During sign-in it returns the completed login plus `recoveryCodes`. From the profile page it returns only `recoveryCodes`. |
| `GET /mfa/status`, `POST /mfa/disable`, `POST /mfa/recovery-codes` | Manage the factor for the signed-in account. |
| `GET /me` | The same profile the login returns, minus the token. The frontend calls it on window focus and after a 403. |
| `POST /refresh` | Presents the current token as `Authorization: Bearer` and returns `{token}` with a new expiry. |
| `POST /logout` | Ends **all** active sessions of the caller (`UmsAuthService.logout` → `terminateAllUserSessions`) and evicts the authority cache. |
| `POST /change-password` | Body `{currentPassword, newPassword}`. `newPassword` must be at least 8 characters, and the full strength rule is applied in `UmsUserService`. |
| `POST /reset-password` | An administrator resets someone else's password. Requires `UMS_EDIT` (`@PreAuthorize`). |

### Login sequence (`UmsAuthController.login`, `UmsAuthService.authenticate`)

1. The CAPTCHA is solved or refused. A refusal returns 401 with `captchaFailed: true`.
2. The user is looked up. If the account is locked (`LoginAttemptService.currentLock`), the request is refused **before** the password is checked.
3. The password is checked against BCrypt. A hash made at an old cost is re-encoded on success (`Passwords.needsRehash`).
4. The account status must be `ACTIVE`. `LOCKED`, `INACTIVE` or any other value is refused (`UmsAuthService.refusalForStatus`).
5. The address and office-hours rules are applied (`AccessRestrictions`, which is off unless `MOHE_ACCESS_RULES` is set). If the address is outside the allowed ranges, the reply is 403 `IP_NOT_ALLOWED`. Outside office hours, the second factor is forced.
6. The second-factor gate is applied (`MfaService.gateFor`): `NONE`, `VERIFY` or `ENROL`. For anything other than `NONE`, the reply is `{mfaRequired, mfaEnrolmentRequired, mfaToken, username}` with **no** session.
7. The login completes (`completedLogin`). A random `jti` is generated, the JWT is signed, and a session row is written. The reply is `{token, user, permissions, permissionMap, countryIds, isMinistryUser, isMinistryLevel, adminTier}`.

A failed login returns 401 with `error`, `remainingAttempts`, `locked`, `lockedIndefinitely` and `lockedUntil`. These values are the same for an unknown username and for a wrong password. For an unknown username the lockout counter is kept in memory only (`LoginAttemptService`, `UmsAuthController.failureBody`).

## The token

The token is signed and read by `Backend/src/main/java/com/dilaw/mohe/ums/service/JwtTokenService.java`. It is HS256, signed with an HMAC key made from `jwt.secret`.

| Claim | Source | Meaning |
|---|---|---|
| `sub` | `.subject(username)` | The username. It is `""` if the username is null. |
| `userId` | `CLAIM_USER_ID` | The numeric user id, which is the identity the filter uses. |
| `jti` | `.id(...)` | The session id. It must match `UMSCORE.UserSessions.TokenHash`. |
| `iat`, `exp` | `issuedAt` / `expiration` | The token lifetime is `jwt.expiration-seconds`. |
| `mfa` | `CLAIM_MFA` | Present only on a pending token, with the value `"pending"`. |

No roles, permissions or country ids are in the token. The filter reloads them on
every request (`UserAuthorityCache`, see below).

**Lifetime.** The code default is 86400 s. `application.properties` sets it to
`${JWT_EXPIRATION_SECONDS:3600}`, so the effective default is one hour
(`application-prod.properties` does the same). A session can be refreshed only until
`jwt.absolute-session-hours` (default 12) after login. `UmsUserSessionService.rotateSessionToken`
compares against `CreatedAt`, which is never moved. A refresh past that limit ends the session and
writes the audit entry `SESSION_ABSOLUTE_EXPIRY`.

**Secret rules.** Startup fails if the secret is blank or shorter than 32 bytes. A secret
containing `only-for-local-dev` or `dev-secret` logs a warning (`JwtTokenService` constructor).

**Key rotation (S-10).** `jwt.secret-previous` (`JWT_SECRET_PREVIOUS`) is used for
verification only. If a token fails the signature check against `jwt.secret`, it is
checked again against the previous key. New tokens are always signed with
`jwt.secret`. Unset the previous secret once `jwt.expiration-seconds` has passed.
If the previous secret equals the current one, it is ignored.

**MFA-pending token (O-16).** `createMfaPendingToken` issues a token with `mfa=pending` that lives
`mohe.mfa.pending-token-seconds` (default 300). It opens no session. `JwtAuthFilter` ignores it,
and `refreshToken` and `/refresh` refuse it, so the only place it is accepted is the
`mfaToken` body field of the `/mfa/*` endpoints (`UmsAuthController.pendingUser`).

### Second factor (`Backend/src/main/java/com/dilaw/mohe/ums/service/MfaService.java`)

- **Who must enrol.** The super admin, ministry and country administrators, and any account holding a privileged code (`*.APPROVE`, `SYSTEM.RESTORE`, `UMS_ADMIN.*`). Setting `mohe.mfa.enforce=false` makes it optional for everyone.
- **Codes.** TOTP uses 6 digits, 30 s steps and HMAC-SHA1 (`ums/service/TotpService.java`). A code is accepted one step either side of now, and each step is accepted only once. There are ten one-time recovery codes. The secret is stored encrypted in `UMSCORE.UserMfa`, and recovery codes in `UMSCORE.UserMfaRecoveryCodes`.
- **Wrong codes.** `mohe.mfa.max-failures` (5) wrong codes lock the factor. The lock lasts as long as `AUTH_LOCKOUT_POLICY` stage 1's `LOCK_MINUTES`, or `mohe.mfa.lock-minutes` if that stage has none (`firstLockMinutes`). As many wrong codes again set the account to `LOCKED`. The account status is checked before any code is examined.

## Per-request checks (`Backend/src/main/java/com/dilaw/mohe/config/JwtAuthFilter.java`)

1. The token is read from `Authorization: Bearer`. For `/api/v1/alerts/stream` only, it comes from `?ticket=` instead (see "SSE ticket" below).
2. The token is parsed (current key, then the previous key). A pending token is treated as no token.
3. If `jwt.session-validation-enabled` (default true) is set, `UmsUserSessionService.findActive(userId, jti)` must find a row in `UMSCORE.UserSessions`. That row must have `TokenHash = jti`, `IsActive = 1` and `ExpiresAt > now`, and the user must be `ACTIVE` and not deleted (`UmsUserSessionRepository.findLiveByUserIdAndJti`). If no row is found, the request continues unauthenticated, and `SecurityConfig` rejects `/api/**`.
4. **Session binding (S-09, `SessionBindingService`, `mohe.session-binding.enabled`).** The request's IP address and the SHA-256 of its `User-Agent` are compared with the session. If **both** differ, the outcome depends on the account:
   - A privileged (MFA-required) account has its session ended. The reply is 401 with header `X-Session-Ended: anomaly` and code `SESSION_ANOMALY`.
   - For any other account, the session is re-bound to the new address and device the first time and ended the second time. Both cases are audited.
5. Authorities are loaded from `UserAuthorityCache`: permission codes plus a tier role (`ROLE_SUPER_ADMIN`, `ROLE_MINISTRY_ADMIN`, `ROLE_MINISTRY_STAFF`, `ROLE_COUNTRY_ADMIN`). The principal is `String.valueOf(userId)`.

### Sessions (`UMSCORE.UserSessions`, `ums/model/UmsUserSession.java`)

`UmsAuthService.createUserSession` writes one row per login. The row holds `TokenHash`, which is the
raw `jti` (a random UUID), plus `IpAddress`, `UserAgent`, `UserAgentHash`, `DeviceInfo`,
`ExpiresAt` and `IsActive`. `/refresh` moves the same row to the new `jti` and extends `ExpiresAt`
(`rotateSessionToken`). There is no fallback to a different session. A user may hold at most
`jwt.max-concurrent-sessions` (default 3) live sessions. Beyond that the oldest is ended and the
audit entry `SESSION_EVICTED` is written (`evictBeyondLimit`).

### Refresh (`UmsAuthController.refresh`)

A refresh is granted only when all three of these hold: the token parses and is not pending, the account is still `ACTIVE` and not deleted (`UmsAuthService.refreshRefusal`), and the session named by the `jti` is still active and within the absolute limit. If any check fails, the reply is 401.

## Lockout and rate limiting

- **Lockout ladder.** `LoginAttemptService` reads its stages from `MOHECORE.AUTH_LOCKOUT_POLICY` (`STAGE_ORDER`, `MAX_ATTEMPTS`, `LOCK_MINUTES`). The counting window is the system parameter `AUTH_ATTEMPT_WINDOW_MINUTES`. The last stage locks the account indefinitely (status `LOCKED`) and ends all of its sessions (`UmsAuthService.authenticate`). The stage values themselves are data, not code.
- **Rate limit.** `LoginRateLimitFilter` allows `rate-limit.login.max-per-minute` (default 10) requests per client IP per minute, then returns 429. It counts `POST /api/v1/ums/auth/login`, `GET /api/v1/ums/auth/captcha` and `POST /api/v1/student-portal/auth/*`, each in its own bucket.

## Must change password (V121, DEC-04)

When an administrator sets a password, `UMSCORE.Users.MustChangePassword` is set. It is cleared when the owner
sets their own password (`UmsUserService.changePassword`: `setMustChangePassword(changedBy != userId)`).
While the flag is set, `MustChangePasswordInterceptor` answers every `/api/**` request with 403
`{"code":"PASSWORD_CHANGE_REQUIRED"}`. The exceptions are `/api/v1/ums/auth/**`, `/users/me`,
`/effective-permissions/me`, sys-settings, code-lists and the student portal. The flag is cached
for 60 s per user (`MustChangePasswordGate`).

## Client side (Angular)

- **Storage.** The token is kept in `localStorage['umsToken']`. The switch `TOKEN_IN_SESSION_STORAGE = false` (`Frontend/src/app/demo/service/ums-auth.service.ts`) would move it to `sessionStorage`. The profile (permissions, tier, and so on) is held in memory only and rebuilt at startup through `/me`. Older `localStorage` keys are purged.
- **Attaching the token.** `Frontend/src/app/demo/interceptors/auth.interceptor.ts` adds `Authorization: Bearer` to every request **except** URLs containing `/student-portal/`.
- **Proactive refresh.** Once `REFRESH_AT = 0.75` of the token's life (`exp - iat`, decoded client-side) has passed, the next outgoing non-auth request first calls `POST /mohe/api/v1/ums/auth/refresh`.
- **401 handling.** On a 401 the client refreshes once and retries the request. Concurrent requests wait on a shared `BehaviorSubject`. The client signs out without retrying in these cases:
  - the refresh fails;
  - the 401 came from `/ums/auth/`;
  - the 401 carries `X-Session-Ended`. The client first stores `sessionStorage['mohe.sessionEnded']` so the login page can explain why.
- **Password change.** `error.interceptor.ts` redirects to `/profile?changePassword=required` on a `PASSWORD_CHANGE_REQUIRED` response.
- **MFA.** `storeSignIn` keeps nothing for a reply without `token` (an MFA challenge). The MFA calls pass `mfaToken` in the body.

## SSE ticket

`EventSource` cannot send headers. The client (`Frontend/src/app/demo/service/alert-stream.service.ts`) calls
`POST /api/v1/alerts/stream/ticket` with the bearer token and then opens
`/api/v1/alerts/stream?ticket=...`. `AlertStreamTicketService` stores the presented JWT under a random ticket
that is single-use and expires after 60 s (`TTL_SECONDS`). `JwtAuthFilter` redeems it, so the session and
binding checks above still apply. A new ticket is issued for every reconnect.

## Student portal (separate credential)

The portal does not use the JWT. `StudentUserAuthService` issues a random 32-hex token (`UUID`), stores only its hash
in `STUDENTCORE.student_sessions` (`PortalTokens.hash`), and gives it a 7-day life (`SESSION_DAYS`).
The client sends it as `X-Student-Portal-Token`. `StudentPortalTokenInterceptor` validates it and falls back to the
legacy `MOHECORE.StudentPortalSession`. Its forced-change code is `PORTAL_PASSWORD_CHANGE_REQUIRED`.
The auth endpoints are under `api/v1/student-portal/auth` (`studentportal/StudentUserAuthController.java`).

## Properties and environment variables

Source: `Backend/src/main/resources/application.properties` unless noted.

| Property | Env var | Default |
|---|---|---|
| `jwt.secret` | `JWT_SECRET` | dev: a local default (`application-dev.properties`); prod: required (`application-prod.properties`) |
| `jwt.secret-previous` | `JWT_SECRET_PREVIOUS` | empty |
| `jwt.expiration-seconds` | `JWT_EXPIRATION_SECONDS` | 3600 |
| `jwt.session-validation-enabled` | `JWT_SESSION_VALIDATION` | true |
| `jwt.absolute-session-hours` | `JWT_ABSOLUTE_SESSION_HOURS` | 12 |
| `jwt.max-concurrent-sessions` | `JWT_MAX_CONCURRENT_SESSIONS` | 3 (0 = unlimited) |
| `rate-limit.login.max-per-minute` | `RATE_LIMIT_LOGIN_MAX_PER_MINUTE` | 10 |
| `mohe.mfa.enforce` / `.issuer` | `MOHE_MFA_ENFORCE` / `MOHE_MFA_ISSUER` | true / `MOHE` |
| `mohe.mfa.encryption-key` / `-previous` | `MOHE_MFA_KEY` / `MOHE_MFA_KEY_PREVIOUS` | empty |
| `mohe.mfa.max-failures` / `.lock-minutes` | `MOHE_MFA_MAX_FAILURES` / `MOHE_MFA_LOCK_MINUTES` | 5 / 15 |
| `mohe.mfa.pending-token-seconds` | `MOHE_MFA_PENDING_TOKEN_SECONDS` | 300 |
| `mohe.session-binding.enabled` | `MOHE_SESSION_BINDING` | true |
| `mohe.access.rules` | `MOHE_ACCESS_RULES` | empty (off) |

## Gaps and open points

- The rows seeded in `AUTH_LOCKOUT_POLICY` (number of stages, attempts, minutes).
- `UmsUserService.changePassword` contains no call that ends the user's other sessions, so a password change appears to leave existing tokens valid. This is inferred from the absence of such a call, not stated anywhere in the code.
