# Security Policy

> **Status:** Proof of Concept. This repository is **not** production-ready
> and must not be used to handle real money, real PII, or real credentials.
> It is an architectural showcase aligned with — but not certified against —
> NIST SP 800-53 Rev 5, SOC 2 Type II, and
> OWASP ASVS L2. Any language like "NIST hardened" in this repo means
> "applies controls drawn from those frameworks", not formal attestation.

## Reporting a Vulnerability

This is a personal learning / showcase repository without a dedicated security
response team. If you find a vulnerability:

1. **Do not open a public issue.**
2. Email the maintainer (see `git log --pretty=format:'%ae' | sort -u`).
3. Allow 14 days for triage before any coordinated disclosure.

## Scope

| In scope | Out of scope |
|---|---|
| Back-end Java code (`poc-fintech-*` modules) | Docker Compose files when run outside localhost |
| React front-end (`poc-fintech-frontend/`) | Third-party dependency CVEs (track via `mvn dependency:tree` / Dependabot) |
| Flyway migrations, Keycloak realm JSON | Performance / DoS findings on default localhost config |
| OpenAPI / REST contract | Social-engineering scenarios |

## Threat Model — Assets & Trust Boundaries

| Asset | Classification | Storage | Primary threats |
|---|---|---|---|
| Access tokens (JWT) | Short-lived credential | Browser memory only (never localStorage) | XSS, replay, audience confusion |
| User identity (`sub`) | PII-adjacent | JWT + MDC + `audit_log.user_id` | Log exfiltration, correlation |
| Account & ledger rows | Sensitive business data | PostgreSQL | SQLi, IDOR, broken authZ |
| Outbox events | Sensitive business data | PostgreSQL → Kafka | Double-publish, poison events |
| Secrets (client secrets, DB passwords) | Secret | `application.yml` (POC only) | Commit leakage — **rotate** before any reuse |

Trust boundaries:

```
[ Browser ]  ──TLS──►  [ Keycloak ]
                 ─Bearer JWT─►  [ Spring Boot API ]  ──►  [ PostgreSQL ]
                                                       ──►  [ Kafka     ]
```

Everything inside the Docker Compose network is treated as one trust zone for
this POC; a real deployment must further segment Kafka and PostgreSQL away
from the API.

## Token Handling & Authentication

### Resource-server validation (production profile)

Tokens are decoded with `NimbusJwtDecoder` configured in `JwtDecoderConfig`,
with a composite validator chain in `JwtValidators.strict(...)`:

| Control | Check | Risk mitigated |
|---|---|---|
| **Algorithm pinning** | `jwsAlgorithms(RS256, PS256)` — HMAC and `none` rejected at parse time | `alg:none` downgrade, RS↔HS key-confusion |
| **Issuer** | `iss` must equal the configured realm URL | Cross-tenant token replay |
| **Audience** | At least one value in `aud` must match `app.security.jwt.audiences` | Confused-deputy (tokens minted for another API) |
| **Clock skew** | Explicit 30 s (60 s in test) on `exp` / `nbf` — not unbounded | Stolen-token replay window |
| **Required claims** | `sub`, `iat`, `exp` must be present and non-blank | Malformed / mint-anything tokens |
| **`azp`** | Must equal `poc-fintech-bff` when set | Tokens minted by a different client |
| **`typ`** | Must be `JWT`, `at+jwt`, or absent | Accidental id_token misuse as access token |

Keycloak emits `aud=poc-fintech-api` via an `audience` client scope
(`oidc-audience-mapper`) attached as a default scope on `poc-fintech-bff`
(`docker/keycloak/fintech-realm.json`).

### Frontend

* Auth library: `keycloak-js` with `pkceMethod: 'S256'` — Authorization Code + PKCE only.
* Tokens live in memory. `localStorage` / `sessionStorage` are **not** used for tokens.
* React Query's `Authorization` header is set per request from the `keycloak`
  instance; expired tokens trigger `updateToken(30)` automatically.

### Method-level authorization

Every `/api/**` controller carries class-level `@PreAuthorize("hasRole('USER')")`
and `@EnableMethodSecurity` is active. Denials map to RFC 7807:

* `401 Unauthorized` — no `Authentication` present.
* `403 Forbidden` — authenticated but missing `ROLE_USER`.

Role mapping from `realm_access.roles` and `resource_access.poc-fintech-bff.roles`
is centralised in `KeycloakJwtAuthoritiesConverter` and shared by both
`SecurityConfig` (prod) and `TestSecurityConfig` (tests).

## HTTP Hardening

All controlled via `SecurityConfig` (profile `!test`):

| Header / Policy | Value | Rationale |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Enforce TLS |
| `X-Content-Type-Options` | `nosniff` | MIME confusion |
| `X-Frame-Options` | `DENY` | Clickjacking |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referrer leak |
| `Cross-Origin-Opener-Policy` | `same-origin` | Spectre / cross-window isolation |
| `Cross-Origin-Embedder-Policy` | `require-corp` | Block resources without CORP opt-in |
| `Cross-Origin-Resource-Policy` | `same-origin` | Prevent cross-origin resource reads |
| `Content-Security-Policy` | `default-src 'self'`; strict `script-src 'self'` + `script-src-attr 'none'`; `object-src 'none'`; `connect-src 'self'`; `upgrade-insecure-requests` | XSS / data exfiltration |
| `Permissions-Policy` | 24 browser features explicitly denied (camera, microphone, geolocation, payment, USB, HID, serial, WebAuthn create/get, screen-capture, …) | Feature-policy hardening |
| `Cache-Control: no-store` | on sensitive API responses | Prevent cached financial data |
| Sessions | `STATELESS` (default) / session-backed (`bff` profile) | No server-side session state unless in BFF mode |
| CSRF | disabled by default; `__Host-XSRF-TOKEN` double-submit under `bff` profile | API is Bearer-authenticated by default |

### Cookie policy (BFF-ready)

| Cookie | Prefix | HttpOnly | Secure | SameSite | Path | Domain | Purpose |
|---|---|---|---|---|---|---|---|
| `__Host-SESSION` | `__Host-` | ✅ | ✅ | Strict | `/` | (absent) | Server-side session identifier |
| `__Host-XSRF-TOKEN` | `__Host-` | ❌ (by design — required for double-submit) | ✅ | Strict | `/` | (absent) | CSRF double-submit token |

The `__Host-` prefix is enforced by the browser per RFC 6265bis §4.1.3.2 — the cookie is
refused if `Secure` is missing, `Path` is not `/`, or `Domain` is set. This binds the
cookie to the exact origin and blocks subdomain cookie-injection attacks.

### Frontend BFF client (`src/api/bffClient.ts`)

* `credentials: 'same-origin'` — **never `'include'`**. Cross-origin requests from the
  SPA are intentionally impossible in the BFF topology (reverse proxy colocates SPA + BFF).
* `mode: 'same-origin'` — cross-origin requests are rejected by the browser itself.
* `redirect: 'manual'` — auth redirects surface as `opaqueredirect` rather than silently
  following into an HTML login page.
* Automatic `X-XSRF-TOKEN` header on mutating methods (POST/PUT/PATCH/DELETE),
  read from the `__Host-XSRF-TOKEN` cookie.

## Input Handling

* **Bean Validation** on all request DTOs (`@Valid`, `@Email`, `@Positive`, `@Pattern`).
* **Jackson 3.x** (`tools.jackson`) with:
  * `FAIL_ON_UNKNOWN_PROPERTIES`, `FAIL_ON_READING_DUP_TREE_KEY`,
    `FAIL_ON_NULL_FOR_PRIMITIVES`, `FAIL_ON_TRAILING_TOKENS`,
    `STRICT_DUPLICATE_DETECTION`.
  * No polymorphic default-typing.
  * ISO-8601 dates only — `write-dates-as-timestamps=false`.
* **No raw SQL** — all persistence goes through Spring Data JPA / named queries.
* **IBAN** validated by `IbanValidator` (ISO 13616 MOD-97).

## Logging & Correlation

* MDC keys populated by `MdcLoggingFilter`:
  `requestId`, `traceId` (W3C), `spanId` (W3C), `userId`, `username`.
* `X-Request-ID` is sanitised against log-injection
  (`^[A-Za-z0-9._:-]{1,128}$`, else regenerated as UUIDv4) — OWASP A09.
* MDC is cleared in `finally` — no cross-request leakage.
* Optional ECS JSON output via `SPRING_PROFILES_ACTIVE=json`.
* Error responses (`GlobalExceptionHandler`) **never** include stack traces or
  implementation details; `server.error.include-stacktrace=never`.

> Incoming `traceparent` headers are accepted verbatim and MUST be treated as
> untrusted labels by downstream log consumers, not as proof of origin.

## Rate Limiting

`RateLimitFilter` enforces configurable limits per principal via a pluggable
`RateLimiter` port and a `BucketPolicyResolver` port. Two storage adapters
ship; pick one with `app.security.rate-limit.backend`:

* **`caffeine`** (default) — in-process bounded sliding window. Per-pod, no
  cross-instance state. Memory hard-capped via `max-tracked-keys`.
* **`redis`** — Bucket4j token bucket on Redis (Lettuce). Distributed across
  pods. The legacy binary `fail-open` switch is superseded by the circuit
  breaker (below); `fail-open` is retained for single-pod deployments that
  don't enable the breaker.

### Per-route policies

A first-match Ant-pattern resolver maps requests to differentiated buckets,
e.g. `/api/v1/transfers/**` → `transfers` (20 req/min), `/api/v1/accounts/**`
→ `accounts` (60 req/min), everything else → `default` (100 req/min).
Configuration lives under `app.security.rate-limit.policies.*` and the
legacy flat `requests-per-minute` is preserved as a back-compat default.

### Circuit-breaker fallback

When `app.security.rate-limit.circuit-breaker.enabled=true`, the production
limiter is wrapped in a Resilience4j `CircuitBreaker` with a Caffeine
sidecar. On Redis failure-rate threshold trip, the breaker `OPEN`s and
calls short-circuit to the in-process Caffeine fallback for the configured
wait duration; per-pod limiting continues. `HALF_OPEN` probes Redis and
either `CLOSE`s on success or re-`OPEN`s on failure. This replaces the
binary fail-open / fail-closed dilemma with graceful degradation.

ArchUnit fitness functions in `HexagonalArchitectureTest` forbid Bucket4j,
Lettuce, Caffeine, and the programmatic Resilience4j `CircuitBreaker` API
from leaking into `RateLimitFilter` — the boundary cannot silently regress.

### Tenant-aware rate-limit keys

Every rate-limit key is namespaced by tenant: authenticated requests use
`tenant:<id>:user:<sub>`, anonymous fall-through `tenant:<id>:ip:<addr>`.
The shipped `JwtClaimTenantResolver` derives `<id>` from the JWT claim
chain `tenant_id → tid → azp` and falls back to the sentinel `shared`
tenant for unauthenticated requests or tokens missing all three claims.
Single-tenant deployments transparently get the `tenant:shared:` prefix.
Hostile claims (containing `:` / wrong shape / over 64 chars) are
rejected and replaced with `shared` — defence against rate-limit-key-
space-injection (OWASP A03 / A05).

### IP-reputation pre-filter

`BlockedIpFilter` runs **before** `RateLimitFilter` and short-circuits
abuse traffic with `403 Forbidden + application/problem+json`
(`urn:fintech:error:blocked-by-reputation`) before any token-bucket cost
is incurred. Lookups go through the `IpReputationService` port; the
shipped `CaffeineIpReputationService` adapter holds an atomic copy-on-
write CIDR snapshot and a `@Scheduled` refresher pulls Spamhaus DROP /
EDROP feeds on `app.security.ip-reputation.refresh-interval` (default
1 h). Refreshes are **fail-static** — a failed pull leaves the last good
snapshot in place, never drops protection. The whole stack is gated by
`app.security.ip-reputation.enabled=true` (default `false`).

### Headers & error contract

Responses carry IETF `RateLimit-Limit / Remaining / Reset` headers
(draft-ietf-httpapi-ratelimit-headers); 429s also include `Retry-After`
and an RFC 7807 problem+json body via `ProblemDetails.of(RATE_LIMITED, …)`.
The legacy `X-RateLimit-Remaining` header was **removed**, including from
`Access-Control-Expose-Headers`.

ArchUnit guards: `BlockedIpFilter` cannot depend on Bucket4j / Lettuce /
Resilience4j; `IpReputationService` and `TenantResolver` implementations
are confined to their dedicated packages.

## Outbox Retention

Published outbox rows are reaped by a scheduled `OutboxShedder`
(`@Scheduled fixedDelay = PT1H` by default) using the cutoff
`now() − app.outbox.shedding.retention` (default `P7D`). Unpublished rows
are never touched — the FOR-UPDATE SKIP LOCKED poller remains the source of
truth for at-least-once delivery. Gated by `app.outbox.shedding.enabled`.
Standards mapping: NIST SP 800-53 SI-12 (Information Management &
Retention), GDPR Art. 5(1)(e) (storage limitation).

## Known Limitations / Non-Goals

1. No mTLS between services.
2. Secrets committed in plain text (POC) — **rotate before any reuse**.
3. `RateLimitFilter` runs cross-instance when `backend=redis`. The default
   `caffeine` backend is per-node and is the right choice for single-pod
   deployments.
4. `audit_log` is now cryptographically chained (V10) with rotatable keys
   (V11) and DB-trigger immutable (V9); silent forgery requires compromise
   of three independent surfaces (DB writer role, trigger drop privilege,
   AND every active HMAC key in the keyring).
5. `/actuator/info` is publicly reachable; `info.*` is curated to be
   non-sensitive, but production should gate it behind `ROLE_admin` or
   network-level ACLs.
6. Keycloak client `poc-fintech-bff` is `public`; for production a BFF
   confidential client is preferred.
7. CSP currently pins `connect-src` to `http://localhost:8180` — must be
   templated per environment before any non-local deploy.
8. The saga orchestrator performs compensation only on credit failure;
   partial failures after debit/credit but before outbox flush are recovered
   by the outbox poller on restart but are not yet observed by an
   end-to-end chaos test.

## Standards Alignment (non-certifying)

The controls above are designed to support and draw from:

* **NIST SP 800-53 Rev 5** — AC-*, AU-*, IA-*, SC-8, SC-18, SC-28, SI-10.
* **NIST SP 800-63B** — AAL2 session binding; HttpOnly cookie as authenticator
  assertion (§7.1), no long-lived bearer credentials in browser storage (§5.2.10).
* **SOC 2 Type II Trust Services Criteria** —
  CC6.1 (logical access: `__Host-` cookie + SameSite=Strict + method security),
  CC6.6 (transmission: HSTS + TLS + Secure cookie),
  CC6.7 (change authorization: CSRF double-submit),
  CC7.2 (system monitoring: MDC + audit trail),
  CC8.1 (change management: ArchUnit fitness functions).
* **OWASP ASVS 4.0.3** — V2 (Authentication), V3 (Session — `__Host-` cookie,
  HttpOnly, Secure, SameSite=Strict), V4 (Access Control), V5 (Validation),
  V7 (Errors & Logging), V13 (API), V14 (Config).
* **OWASP Top 10 2021** — A01, A02, A05, A07, A08, A09.
* **IETF `draft-ietf-oauth-browser-based-apps`** — BFF section.
* **PCI DSS v4.0.1** — applicable subset (no PAN handled):

  | Requirement | Where implemented |
  |---|---|
  | **2.2.1** Secure configuration standards | `SecurityConfig` + `BffSecurityConfig` hardened headers (CSP, HSTS, COOP/COEP/CORP, Permissions-Policy) |
  | **3.3** Protect sensitive account data when displayed | `IbanMasking` util (`poc-fintech-domain/.../util/IbanMasking.java`) — used in logs / UI |
  | **4.2.1** Strong cryptography on open networks | HSTS preload 1 y, `Secure` cookies, TLS 1.3 ciphers |
  | **6.2.4** Common coding vulnerabilities | Bean Validation, Jackson 3 strict deserialisation, CSRF double-submit (BFF) |
  | **6.3.1 / 6.3.3** Vulnerability identification & remediation | `mvn -Ppci-scan verify` → OWASP Dependency-Check fails build on CVSS ≥ 7.0 |
  | **6.4.2** Public-facing web-app protection | JWT ultra-validation (issuer, aud, azp, algs, required claims) |
  | **7.2** Access by business need-to-know | `@PreAuthorize` + user-scoped query use cases |
  | **8.2.8** Re-authenticate after 15 min inactivity | `server.servlet.session.timeout: 15m` |
  | **8.3.1** Strong authentication | OAuth2 Authorization Code + PKCE (S256) |
  | **10.2.1** Log all user access | `@Audited` AOP → `audit_log` — V5 + AuditAspect |
  | **10.3.4 / 10.5.2** Audit-log integrity | Flyway V9 PostgreSQL `BEFORE UPDATE/DELETE/TRUNCATE` immutability triggers on `audit_log` |
  | **10.5** Tamper evidence | Flyway V10 + `AuditChainWriter` / `AuditChainVerifier` — HMAC-SHA256 chain; `/actuator/auditchain` surfaces divergence |
  | **10.5 / §3.7** HMAC key rotation | Flyway V11 + `AuditChainKeyRing` (multi-key, per-row `key_id`); rotation supported via keyring config |
  | **10.7** Audit-log retention (12 months) | Append-only table; archival procedure documented in migration V9 comment |

No formal certification is claimed or implied.

## See Also

* [`README.md`](./README.md) — architecture overview, JWT validation table, method-security matrix.
* `docker/keycloak/fintech-realm.json` — realm, clients, scopes, roles, audience mapper.
