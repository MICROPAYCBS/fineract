# UI Agent Prompt: Staff TOTP / Global 2FA Delivery Method

Implement UI support for tenant-global two-factor delivery method selection and authenticator-app (TOTP) enrollment for staff login.

## Backend contract (already implemented)

- Feature gate: `fineract.security.2fa.enabled` (env `FINERACT_SECURITY_2FA_ENABLED`).
- Global method (exactly one): configure via `PUT /v1/twofactor/configure` with:
  ```json
  { "otp-delivery-method": "email" | "sms" | "totp" }
  ```
- `GET /v1/twofactor/configure` returns `otp-delivery-method` plus existing template/TTL keys. Email/SMS enable flags are synced from the selected method.
- Login (`POST /v1/authentication`) when 2FA is on returns:
  - `isTwoFactorAuthenticationRequired`
  - `deliveryMethod` (`email` | `sms` | `totp`)
  - `totpEnabled`
  - `totpEnrollmentRequired` (`true` when method is `totp` and user has not enrolled)
- Delivery methods: `GET /v1/twofactor` returns **at most one** method matching the global policy (TOTP only if enrolled).
- SMS/Email: unchanged — `POST /v1/twofactor?deliveryMethod=...` then `POST /v1/twofactor/validate?token=...`.
- TOTP login (enrolled): `POST /v1/twofactor/validate?token={authenticatorCode}` (optional `POST /v1/twofactor?deliveryMethod=totp` for symmetry).
- TOTP enroll (password-authenticated, no TFA token yet):
  - `POST /v1/twofactor/totp/enroll` → `{ secret, otpauthUri }`
  - `POST /v1/twofactor/totp/confirm?token={code}` → `{ totpEnabled: true }`
- Admin lost-device reset: `POST /v1/users/{userId}?command=resetTotp` (permission `RESETTOTP_USER`).
- User GET includes read-only `totpEnabled` (never the secret).
- After 2FA success, send header `Fineract-Platform-TFA-Token` as today.

## UI requirements

### 1. Two-factor settings (admin)

- Single selector: **Email / SMS / Authenticator app** bound to `otp-delivery-method`.
- Do **not** show independent “enable email” + “enable SMS” toggles as the primary control.
- Keep SMS provider / email & SMS template fields when those methods are selected.
- Require `UPDATE_TWOFACTOR_CONFIGURATION`.

### 2. Login second step

- If `totpEnrollmentRequired`: show enroll wizard (QR from `otpauthUri` or manual secret entry) → confirm with 6-digit code → then proceed to validate / obtain TFA token.
- If method is `totp` and enrolled: code entry only (no “send OTP”).
- If method is `email` or `sms`: existing request OTP → enter code flow.
- No multi-method picker at login.

### 3. Users admin

- Show whether authenticator is enrolled (`totpEnabled`).
- Action **Reset authenticator** → `POST /users/{id}?command=resetTotp`.
- No per-user “require TOTP” checkbox.

## Out of scope

- WebAuthn, backup codes, client/mobile banking MFA, OIDC IdP MFA.
