# Agent Prompt: Single-Session Enforcement & User Session Management UI (Next.js + shadcn/ui)

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Adapt the web app to the new backend **single-session enforcement** feature:

1. **Global forced sign-out handling** — any API call can now return a 401 meaning "this session was terminated" (kicked by a newer login on another device, or revoked by an admin). Detect it centrally, clear auth state, and land the user on the login page with an explanatory message.
2. **Active Sessions panel** — on the user detail page under Administration → Users, show the user's active two-factor sessions (device, IP, validity) with a **Revoke** action.
3. **RBAC** — gate the new panel and action on the new Fineract permissions.

Extend the existing auth/user-admin scaffold — do not rebuild the login flow. Follow repository conventions for routing, server actions, validation, and RBAC.

## Background

The staff web app authenticates with basic auth + two-factor: after OTP validation, `POST /twofactor/validate` returns an access token that is sent on every request as the **`Fineract-Platform-TFA-Token`** header. The backend now treats that token as the **server-side session**:

- A new global configuration **`enforce-single-session`** (disabled by default; `value` = max concurrent sessions, default 1) makes each new login **revoke the user's older sessions** beyond the limit. The kicked device finds out on its **next API call** — there is no push; do not build any real-time notification.
- Tokens now record the **IP address and user agent** captured at login. The backend reads `X-Forwarded-For`, so nothing new is sent by the client — the `/twofactor/validate` request and response shapes are **unchanged**.
- An admin can now **list and revoke** any user's sessions via new endpoints (below). Revocation also takes effect on the victim's next API call.
- Sessions end for one of two recorded reasons: `SUPERSEDED_BY_NEW_LOGIN` (single-session kick) or `REVOKED_BY_ADMIN`.

## Backend deployment prerequisite

Fineract must include Liquibase migrations **`0246_add_single_session_enforcement.xml`** and **`0247_add_user_session_permissions.xml`**, and run with `fineract.security.2fa.enabled=true`. When 2FA is disabled the session endpoints do not exist (404). Single-session kicks only happen when the `enforce-single-session` global configuration is enabled for the tenant — but build the 401 handling unconditionally, since admin revocation works regardless.

Base path: `/fineract-provider/api/v1`. Standard Fineract auth + `Fineract-Platform-TenantId` header (reuse `createFineractClient()`).

---

## Behaviour change: session-terminated 401

Any authenticated request can return **HTTP 401** with the response header:

```
Fineract-Platform-Reason: session-superseded
```

This means the stored TFA token was revoked because the account signed in on another device. Handle it in the central fetch/response layer (wherever 401s are already intercepted):

1. Clear stored credentials and TFA token (normal logout cleanup).
2. Redirect to login with a distinct message: **"You were signed out because your account signed in on another device."**
3. Do **not** retry the request.

Two important nuances:

- **Rely on the status code + header, not the body.** The 401 body is a servlet-container error payload, not a Fineract `errors[]` envelope.
- An **admin-revoked** session gets a plain 401 (`Invalid two-factor access token provided`, no `Fineract-Platform-Reason` header) — the same response as an expired token. Treat any TFA-layer 401 as "session ended, sign in again"; only the message differs when the header is present.

---

## API contract

### List a user's sessions

| Endpoint | Method | Purpose | Fineract permission |
|---|---|---|---|
| `/users/{userId}/sessions` | GET | Active (enabled) sessions, newest first | `READ_USERSESSION` — **not required when `userId` is the signed-in user** (self-view is always allowed) |
| `/users/{userId}/sessions/{sessionId}/revoke` | POST | Revoke one session (empty body) | `REVOKE_USERSESSION` (admin only, including own sessions) |
| `/usersessions/history` | GET | Paginated login history across **all** users, incl. revoked/expired sessions | `READ_USERSESSION` (no self exemption) |

**GET response (implement types from this contract):**

```json
[
  {
    "id": 42,
    "userId": 7,
    "username": "jdoe",
    "validFrom": [2026, 8, 12, 9, 30, 0],
    "validTo": [2026, 8, 12, 17, 30, 0],
    "ipAddress": "10.20.4.15",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
    "active": true,
    "revocationReason": null
  }
]
```

- **Token values are never returned** — a session listing cannot leak a usable credential. This also means there is no reliable "this is your current device" indicator; do not fake one.
- `validFrom` / `validTo` serialize in the same date format as `accessToken.validFrom` already returned by `POST /twofactor/validate` — reuse the existing parsing helper.
- `active: false` means the token is still enabled but its validity window has lapsed (expired session). Show it with an **Expired** badge; it is still revocable.
- `ipAddress` (max 45 chars) and `userAgent` (max 500 chars) are nullable — sessions issued before this deployment have neither.
- 404 with standard Fineract error envelope when the user id does not exist.

**POST revoke:** no request body required. Response is a standard `CommandProcessingResult` (`{ "entityId": 42, ... }`). The action goes through the command framework, so it appears in the audit trail and would honour maker-checker if enabled on the permission.

### Login history (all users)

`GET /usersessions/history` — query params, all optional:

| Param | Meaning |
|---|---|
| `userId` | Restrict to one user |
| `fromDate` / `toDate` | Login date range, ISO `yyyy-MM-dd`, `toDate` inclusive |
| `offset` / `limit` | Standard Fineract paging; `offset` must be a multiple of `limit`; limit default 50, max 200 |

Response is the standard Fineract page envelope over the same session shape:

```json
{
  "totalFilteredRecords": 1234,
  "pageItems": [
    {
      "id": 42,
      "userId": 7,
      "username": "jdoe",
      "validFrom": [2026, 8, 12, 9, 30, 0],
      "validTo": [2026, 8, 12, 17, 30, 0],
      "ipAddress": "10.20.4.15",
      "userAgent": "Mozilla/5.0 ...",
      "active": false,
      "revocationReason": "SUPERSEDED_BY_NEW_LOGIN"
    }
  ]
}
```

- `revocationReason`: `"SUPERSEDED_BY_NEW_LOGIN"` \| `"REVOKED_BY_ADMIN"` \| `null` (null + `active:false` = expired naturally; null + `active:true` = still live).
- Each row is one successful login. **Failed login attempts are not in this data**, and logins by `BYPASS_TWOFACTOR` users (system accounts, super user) never appear — say so in the screen's help text so auditors are not misled.
- Invalid `fromDate`/`toDate` returns the standard Fineract validation error envelope (`validation.msg.usersession.history.invalid.date`).

### Expected backend error codes

Map `errors[].userMessageGlobalisationCode` via the existing error-translation pattern.

| Code | When | UI hint |
|---|---|---|
| `error.msg.usersession.not.found` | Session id unknown, belongs to a different user, or already revoked | "Session no longer active." Refresh the list. |
| `error.msg.user.id.invalid` (existing) | Unknown user | Standard not-found handling |

---

## RBAC

Add to `packages/auth/permissions.manifest.json`:

| Manifest key | Fineract permission |
|---|---|
| `administration.users.sessions` | `READ_USERSESSION` |
| `administration.users.sessions.revoke` | `REVOKE_USERSESSION` |

The backend migration grants `READ_USERSESSION` to every role that has `READ_USER`, and `REVOKE_USERSESSION` to every role with `UPDATE_USER`, so existing user-admin roles see the panel without manual role edits. Still gate both the section and the button on the manifest keys.

---

## UI design: Active Sessions panel

Add a **Sessions** section (card or tab, following the page's existing layout idiom) to the user detail page under Administration → Users:

1. **Load** `GET /users/{userId}/sessions` when the section renders. Gate on `administration.users.sessions`.
2. **Table columns:** Signed in (`validFrom`, formatted date-time) · Expires (`validTo`) · IP address · Device (`userAgent`, truncated with full value in a tooltip) · Status badge (**Active** / **Expired**) · Revoke button.
3. **Device column:** a light user-agent summary (browser + OS) is fine if a helper already exists; otherwise show the truncated raw string — do **not** add a UA-parsing dependency for this.
4. **Revoke:** confirm dialog — "Revoke this session? The device will be signed out on its next action." On success: toast + refresh the list (`router.refresh()` / revalidate, existing pattern).
5. **Empty state:** "No active sessions."
6. **Self-revocation:** an admin viewing their own user can revoke their own session and will be signed out on their next request — the global 401 handler covers this; no special casing needed.

## UI design: Login History (admin)

Add a **Login History** screen under Administration (sibling of Users, or a tab beside it),
gated on `administration.users.sessions`:

1. **Table** over `GET /usersessions/history`: Username · Signed in (`validFrom`) · IP ·
   Device (truncated UA) · Status (Active / Expired / badge with revocation reason).
2. **Filters:** user picker (existing users lookup → `userId`), date range → `fromDate`/`toDate`.
3. **Pagination:** standard offset/limit table paging against `totalFilteredRecords`.
4. Username cell links to the user's detail page (where the live Sessions panel and Revoke live —
   the history screen itself is read-only).

## UI design: My Sessions (profile)

Because self-view needs no permission, also surface a read-only **My Sessions** list on the
signed-in user's own profile/account page, calling `GET /users/{ownUserId}/sessions` with the
`userId` returned at login. Same table as the admin panel minus the Revoke button (self-revoke
still requires `REVOKE_USERSESSION`). This lets any staff member spot a login they don't
recognise and report it. Do not gate this section on `administration.users.sessions`.

## UI design: login page message

Support the forced-sign-out redirect from the 401 handler (query param or auth-state flag, matching how session-expiry messaging works today):

- Superseded: "You were signed out because your account signed in on another device."
- Generic TFA 401: existing session-expired message.

---

## TypeScript types (single source)

Define in `@mifos/api-client`:

```typescript
export interface UserSession {
  id: number;
  validFrom: string; // parse with the existing Fineract date helper
  validTo: string;
  ipAddress: string | null;
  userAgent: string | null;
  active: boolean;
}
```

No Zod schema is needed for revoke (no body); validate only that `sessionId` is a positive integer in the server action.

---

## Testing expectations

### Unit

- 401 + `Fineract-Platform-Reason: session-superseded` → superseded sign-out path (distinct message).
- 401 without the header on a TFA-authenticated request → generic session-expired path.
- Sessions table renders nullable `ipAddress`/`userAgent` rows without crashing.

### Manual E2E (against Fineract with migrations 0246/0247, 2FA on)

1. Enable `enforce-single-session` in Global Configuration. Sign in on browser A, then browser B with the same user. Next action in browser A → redirected to login with the "signed in on another device" message.
2. Disable the config → both browsers stay signed in concurrently.
3. As an admin, open the user's Sessions panel — verify IP/device/validity rows; revoke the user's session; the user's next action signs them out (generic message).
4. Revoke an already-revoked session (stale list) → "Session no longer active" toast, list refreshes.
5. Sign in with a role lacking `READ_USERSESSION` → Sessions section hidden.

---

## Out of scope (do not build now)

- Real-time/push notification of forced sign-out (kick is lazy, on next request — by design).
- "This device" indicator on the sessions list (token values are never exposed).
- A self-service "sign out my other devices" screen (backend supports it via the same endpoints, but defer the UX).
- Admin UI for the `enforce-single-session` configuration — the existing Global Configuration screen already covers it.
- Any change to the login / OTP / validate flow — request and response shapes are unchanged.

---

## Constraints

- Handle the 401 in **one** central place; do not sprinkle per-call handling.
- Rely on the `Fineract-Platform-Reason` header, never on parsing the 401 body.
- Gate routes and buttons on the manifest keys above.
- After a successful revoke, refresh via the existing revalidation pattern — do not optimistically remove rows.
