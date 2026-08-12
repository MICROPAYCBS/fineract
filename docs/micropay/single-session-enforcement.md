# Single-Session Enforcement — Rollout & Policy Runbook

Staff sessions in Fineract ride on the two-factor access token (`Fineract-Platform-TFA-Token`),
which is validated server-side on every API request. This feature set turns that token into a
managed session: concurrent logins can be capped per user, sessions carry device metadata, and
admins can list and revoke them.

## Components (shipped on the `micropay` branch)

| Piece | Where |
|---|---|
| Single-session enforcement at token issuance | `TwoFactorServiceImpl` (`enforce-single-session` global config) |
| IP / user-agent capture on login | `POST /twofactor/validate` |
| Distinct 401 for kicked sessions | `Fineract-Platform-Reason: session-superseded` header |
| Sessions list / revoke API | `GET /users/{userId}/sessions`, `POST /users/{userId}/sessions/{sessionId}/revoke` |
| Permissions | `READ_USERSESSION`, `REVOKE_USERSESSION` (backfilled from `READ_USER` / `UPDATE_USER`); viewing one's own sessions requires no permission |
| Migrations | tenant parts `0246`, `0247`, `0248` |
| Frontend adaptation guide | `docs/prompts/user-sessions-ui-agent-prompt.md` |

## Deployment steps (per environment)

1. **Enable two-factor**: set `FINERACT_SECURITY_2FA_ENABLED=true`. Everything in this feature
   lives inside the 2FA filter path — with 2FA off there are no sessions to manage and the
   endpoints do not exist.
2. **Deploy + migrate**: migrations 0246–0248 run automatically. 0248 tightens token lifetimes
   to 12h standard / 48h extended **only if the tenant still has the upstream defaults**
   (24h / 7d); tuned values are left alone and remain adjustable via `PUT /twofactor/configure`.
3. **Turn on enforcement**: enable the `enforce-single-session` global configuration for the
   tenant (Admin → Global Configuration). Its `value` is the maximum concurrent sessions per
   user (default 1). Leave it disabled to get session visibility/revocation without the cap.
4. **Frontend**: deploy the web app changes from `docs/prompts/user-sessions-ui-agent-prompt.md`
   (forced sign-out handling + sessions panel). The backend is safe to deploy first; until the
   web app handles the superseded 401, kicked users just see the generic session-expired error.

## Policy: BYPASS_TWOFACTOR

Users holding `BYPASS_TWOFACTOR` skip the two-factor filter entirely — **no session record, no
session cap, no revocation**. They authenticate with basic auth alone. Policy:

- **Staff roles must not carry it.** Any staff user with bypass is invisible to session
  enforcement.
- **System/integration accounts should carry it** (deliberately exempt from the cap, so
  machine-to-machine calls never kick each other).
- **Super user (role id 1) currently carries it** — granted in micropay migration `3094` because
  2FA's permission check does not honour `ALL_FUNCTIONS`, and without bypass the super user is
  locked out before TOTP enrollment. Accepted tradeoff for bootstrap; revisit once day-to-day
  administration happens through named admin accounts. Keep the super user credential in the
  vault, not in daily use.

Audit query (run per tenant, should return only system accounts and the super user role):

```sql
SELECT r.id AS role_id, r.name AS role_name, u.id AS user_id, u.username
FROM m_role r
JOIN m_permission p_check ON p_check.code = 'BYPASS_TWOFACTOR'
JOIN m_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p_check.id
LEFT JOIN m_appuser_role ur ON ur.role_id = r.id
LEFT JOIN m_appuser u ON u.id = ur.appuser_id AND u.is_deleted = FALSE
ORDER BY r.id, u.id;
```

## Token lifetime policy

| Setting (`twofactor_configuration`) | Value after 0248 | Meaning |
|---|---|---|
| `access-token-live-time` | 43200 (12h) | Standard session: bounded to a working day |
| `access-token-live-time-extended` | 172800 (48h) | "Remember me" (`extendedToken=true` on OTP request) |

Absolute expiry is the server-side backstop; the client-side idle timeout
(`session-idle-timeout-minutes` global config) still handles short-term inactivity. If strict
single-day sessions are required, decide whether the web app should stop offering
`extendedToken` for staff — extended tokens count toward the session cap but live longer.

## Operational notes

- **Kicks are lazy.** A revoked/superseded device is cut off on its *next* API call, not pushed
  out instantly.
- **Revocation is audited.** Admin revokes go through the command framework
  (`USERSESSION`/`REVOKE`) and appear in the audit trail; `twofactor_access_token.revocation_reason`
  records `SUPERSEDED_BY_NEW_LOGIN` or `REVOKED_BY_ADMIN` for forensics.
- **Terminated-employee kill switch**: disable the user, then revoke their sessions from the
  user detail page (or the revoke endpoint). Effective on their next request.
- **Multi-node caveat**: token validation is cached per JVM (`userTFAccessToken` cache).
  Revocation evicts the cache on the node that processes it. If Fineract ever runs as multiple
  instances behind a load balancer, kicked sessions survive on other nodes until the cache entry
  expires — the cache must become distributed (or be bypassed for token lookups) before scaling
  out. Single-instance deployments are unaffected.
- **Sessions created before this deployment** have no IP/user-agent recorded; columns are null
  until the user next signs in.
