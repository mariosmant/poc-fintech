# POC Fintech

> A reference-quality, security-hardened fintech back-end + SPA showcasing the
> patterns I'd reach for on a real money-movement system: Hexagonal/DDD/CQRS,
> Saga + Transactional Outbox, double-entry ledger, OAuth2 (Resource-Server **or**
> BFF), tamper-evident audit log, and supply-chain hardening — all on the latest
> Java 25 / Spring Boot 4.0 / React 19 stack.

**Stack:** Java 25 · Spring Boot 4.0.5 · Spring Framework 7 · Jackson 3.x
(`tools.jackson`) · React 19 + TypeScript 5.7 · PostgreSQL 18 · Apache Kafka
(KRaft) · Keycloak (OIDC/OAuth2) · OpenTelemetry · Prometheus · Grafana.

## Highlights for reviewers

If you only have five minutes, look here:

| What | Where |
| --- | --- |
| **Saga orchestration with compensation** — debit → fraud → FX → credit, rollback on credit failure | [`TransferSagaOrchestrator`](poc-fintech-application/src/main/java/com/mariosmant/fintech/application/saga/TransferSagaOrchestrator.java) |
| **Transactional outbox + idempotent consumer** — exactly-once over Kafka | [`OutboxPollingPublisher`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/messaging/publisher/OutboxPollingPublisher.java), [`TransferSagaEventConsumer`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/messaging/consumer/TransferSagaEventConsumer.java) |
| **Hexagonal boundaries enforced by ArchUnit** — fitness functions block adapter leakage into filters | [`HexagonalArchitectureTest`](poc-fintech-boot/src/test/java/com/mariosmant/fintech/arch/HexagonalArchitectureTest.java) |
| **Tamper-evident audit log** — per-row HMAC-SHA256 chain + key rotation + `/actuator/auditchain` verifier | [`AuditChainWriter`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/audit/AuditChainWriter.java), [`AuditChainVerifier`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/audit/AuditChainVerifier.java) |
| **Ultra-strict JWT validation** — alg pinning, audience, azp, typ, required claims, bounded skew | [`JwtValidators`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/JwtValidators.java) |
| **Two auth topologies** — OAuth2 Resource Server (SPA-held Bearer) **or** BFF (server-held tokens, `__Host-SESSION` cookie, double-submit CSRF) | [`SecurityConfig`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/SecurityConfig.java), [`BffSecurityConfig`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/BffSecurityConfig.java), [`bffClient.ts`](poc-fintech-frontend/src/api/bffClient.ts) |
| **Rate limiting with circuit-breaker fallback** — Bucket4j+Redis distributed; Caffeine in-process; tenant-aware keys; IETF `RateLimit-*` headers | [`security/ratelimit/`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/ratelimit/) |
| **IP-reputation pre-filter** — Spamhaus DROP/EDROP refresher, fail-static snapshot | [`security/reputation/`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/reputation/) |
| **Hardened distroless image** — UID 65532, no shell; two variants via `docker buildx bake` | [`Dockerfile`](Dockerfile), [`docker-bake.hcl`](docker-bake.hcl) |
| **Standards mapping** — PCI DSS v4.0.1, NIST SP 800-53, OWASP ASVS, SOC 2 | [`SECURITY.md`](SECURITY.md) |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React 19 + TypeScript + Vite + TailwindCSS)      │
│  (Dashboard, Accounts, Transfers, Ledger, React Query)      │
│  (Keycloak JS OIDC auth, PKCE, protected routes)            │
├─────────────────────────────────────────────────────────────┤
│                     Boot Module                             │
│  (Spring Boot entry point, config, Docker, tests)           │
├─────────────────────────────────────────────────────────────┤
│                Infrastructure Module                        │
│  (Spring adapters: JPA, Kafka, REST, Security, Observ.)     │
│  (OAuth2 Resource Server, Audit AOP, MDC Logging, DLQ)      │
├─────────────────────────────────────────────────────────────┤
│                Application Module                           │
│  (CQRS handlers, Saga Orchestrator, Use Cases, DTOs)        │
├─────────────────────────────────────────────────────────────┤
│                  Domain Module                              │
│  (Aggregates, Value Objects, Events, Ports — pure Java)     │
└─────────────────────────────────────────────────────────────┘
        ↕                   ↕                    ↕
   Keycloak            PostgreSQL              Kafka
   (OAuth2/OIDC)       (+ Flyway)          (KRaft mode)
                            ↕                    ↕
                        Redis (opt.)        OTel Collector
                  (BFF session / rate-limit) (traces → Prom)
```

## What This POC Demonstrates

| Pattern / Practice | Implementation |
| --- | --- |
| **Hexagonal Architecture** | Domain ports (interfaces) + Infrastructure adapters |
| **DDD** | Aggregates (`Account`, `Transfer`), Value Objects (`Money`, `AccountId`), Domain Events |
| **CQRS** | Command handlers (write) / Query handlers (read) split |
| **Saga Orchestrator** | `TransferSagaOrchestrator` — multi-step transfer flow with compensation |
| **Transactional Outbox** | Events written to `outbox_events` table atomically with state changes |
| **Exactly-Once Processing** | Outbox + idempotent consumer (idempotency keys) |
| **IBAN-first Banking UX** | Accounts expose a valid ISO 13616 IBAN (MOD-97 check digits) generated server-side; UI displays IBAN in groups of 4 with copy-to-clipboard. UUID remains the canonical PK. Transfers accept either an internal `targetAccountId` (own accounts) or a `targetIban` (beneficiary lookup) — "exactly one of" enforced via Bean Validation. |
| **Mockito Java Agent** | `mockito-core` attached via Surefire/Failsafe `-javaagent:${org.mockito:mockito-core:jar}` (resolved by `maven-dependency-plugin:properties`) — removes the JDK self-attach deprecation warning. |
| **Event-Driven (Kafka)** | `OutboxPollingPublisher` → Kafka → `TransferSagaEventConsumer` |
| **Dead Letter Queue (DLQ)** | Failed Kafka messages → `transfer-events.DLT` → persisted to `dead_letter_queue` table |
| **Optimistic Locking** | JPA `@Version` — no pessimistic locks |
| **Double-Entry Ledger** | `LedgerEntry` — balanced debit/credit accounting |
| **Multi-Currency FX** | `FxRateAdapter` — rate triangulation through USD |
| **Fraud Detection** | `FraudDetectionAdapter` — rule-based with circuit breaker |
| **Circuit Breaker + Retry** | Resilience4j with exponential backoff (fraud, FX, Kafka) |
| **PCI DSS v4.0.1 Alignment** | IBAN masking (Req 3.3), 15-min idle session (Req 8.2.8), audit-log DB-immutability trigger (Req 10.3.4/10.5.2), OWASP Dependency-Check CI (Req 6.3.1/6.3.3) |
| **Tamper-Evident Audit Log** | Per-row HMAC-SHA256 chain (`prev_hash`, `row_hash`, `chain_seq`) + `/actuator/auditchain` verifier — detects privileged-user tampering that bypasses the V9 immutability triggers (NIST AU-9(3), PCI DSS §10.5) |
| **Audit Trail (AOP)** | `@Audited` aspect → `audit_log` table with user ID, IP, action, timestamps (NIST AU-2) |
| **MDC Structured Logging** | userId, requestId, traceId on every log line (SLF4J2) |
| **Rate Limiting** | Per-route, per-principal limits via `RateLimiter` + `BucketPolicyResolver` ports; Bucket4j+Redis distributed adapter or Caffeine in-process; Resilience4j circuit-breaker fallback; IETF `RateLimit-*` headers + RFC 7807 429 bodies |
| **Multi-Tenant Rate-Limit Keys** | `TenantResolver` port + `JwtClaimTenantResolver` (JWT `tenant_id → tid → azp` lookup chain); rate-limit keys namespaced as `tenant:<id>:user:<sub>` |
| **IP-Reputation Pre-Filter** | `BlockedIpFilter` + `IpReputationService` port with Spamhaus DROP / EDROP feed refresher; CIDR snapshot is fail-static; 403 + RFC 7807 `urn:fintech:error:blocked-by-reputation` |
| **Retention / Data Minimisation** | `OutboxShedder` scheduled sweep deletes published outbox rows older than `app.outbox.shedding.retention` (NIST SI-12, GDPR Art. 5(1)(e)) |
| **Supply Chain & Container** | Distroless multi-stage Dockerfile (UID 65532, no shell); two variants baked at build time (`resource-server` default, `bff`); `docker buildx bake` target ships both |
| **BFF Topology (optional)** | `bff` Spring profile + Spring Session + OAuth2 Login. SPA holds no token — only an HttpOnly `__Host-SESSION` cookie. `BffController` exposes `/bff/user`, `/bff/logout`, `/bff/public/csrf`. Frontend `bffClient.ts` does same-origin fetch + double-submit `X-XSRF-TOKEN`. |
| **Observability** | Micrometer + Prometheus + Grafana + OpenTelemetry (OTLP HTTP → otel-collector with tail-based sampling → Prometheus) |
| **OpenAPI** | Springdoc auto-generated Swagger UI with JWT security scheme |

## Tech Stack

Versions explicitly pinned in [`pom.xml`](pom.xml) / [`poc-fintech-frontend/package.json`](poc-fintech-frontend/package.json) are listed exactly; everything else inherits from the Spring Boot 4.0.5 BOM.

| Technology | Version | Source |
|---|---|---|
| Java | 25 | `pom.xml` (`java.version`) |
| Spring Boot | 4.0.5 | parent POM |
| Spring Framework | 7.x | managed by Spring Boot 4.0 BOM |
| Jackson | 3.x (`tools.jackson`) | managed by Spring Boot 4.0 BOM |
| Resilience4j | 2.3.0 | `pom.xml` (`resilience4j.version`) |
| Springdoc OpenAPI | 2.8.6 | `pom.xml` (`springdoc.version`) |
| Bucket4j (jdk17 core / redis-common / lettuce) | 8.18.0 | `pom.xml` (`bucket4j.version`) |
| OWASP Dependency-Check | 12.1.0 | `pci-scan` profile |
| Flyway | managed by Spring Boot BOM | `application.yml` |
| Testcontainers | managed by Spring Boot BOM | test scope |
| PostgreSQL | 18-alpine | `docker-compose.yml` |
| Apache Kafka | latest (KRaft, no Confluent, no ZooKeeper) | `docker-compose.yml` |
| Keycloak | latest | `docker-compose.yml` (`quay.io/keycloak/keycloak`) |
| OpenTelemetry Collector (contrib) | 0.111.0 | `docker-compose.yml` |
| Kafka UI (provectuslabs) | latest | `docker-compose.yml` |
| Prometheus / Grafana | latest | `docker-compose.yml` |
| React + TypeScript | 19 + ~5.7 | `package.json` |
| Vite | ^6.0 | `package.json` |
| TailwindCSS | ^3.4 | `package.json` |
| @tanstack/react-query | ^5.62 | `package.json` |
| keycloak-js | ^26.2.3 | `package.json` |
| react-router-dom | ^7.1 | `package.json` |
| Vitest | ^2.1.8 | `package.json` |

## Security Architecture

The backend ships with **two interchangeable auth topologies** baked at build
time (see [`Dockerfile`](Dockerfile) `APP_VARIANT` arg). Pick the one that
matches your threat model — both share the same hardened response headers,
audit log, rate limiter, and method security; only the credential-handling
layer differs.

### Variant A — OAuth2 Resource Server (default, SPA-held Bearer)

```text
┌──────────┐    PKCE/OIDC     ┌──────────┐     JWT Bearer     ┌──────────┐
│  React   │ ◄──────────────► │ Keycloak │                    │  Spring  │
│ Frontend │                  │   IdP    │                    │  Boot    │
│          │ ───────────────────────────────────────────────► │ Resource │
│          │   fetch + Authorization: Bearer <jwt>            │  Server  │
└──────────┘                  └──────────┘                    └──────────┘
                                                                   │
                                                          JWT validated via
                                                          JWK Set endpoint
                                                          + JwtValidators chain
```

- **Keycloak client**: `poc-fintech-spa` (public, PKCE S256).
- **Token storage**: in-memory only — `keycloak-js` keeps the access/refresh
  token in JS heap; **never** `localStorage` / `sessionStorage`.
- **Token refresh**: `keycloak.updateToken(30)` every 30 s
  ([`AuthProvider.tsx`](poc-fintech-frontend/src/auth/AuthProvider.tsx)).
- **Backend session**: `STATELESS` — no `JSESSIONID`, no server-side state.
- **CSRF**: not needed — Bearer tokens are not ambient credentials and
  `Authorization` cannot be forged from a cross-site context.
- **`@PreAuthorize("hasRole('USER')")`** class-level on every `/api/**` controller.
- **Filter chain**: [`SecurityConfig`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/SecurityConfig.java) (active when `bff` profile is **off**).

### Variant B — BFF (server-held tokens, `__Host-` cookies)

```text
┌──────────┐  same-origin fetch  ┌──────────┐   Auth Code+PKCE   ┌──────────┐
│  React   │ ─ __Host-SESSION ─► │  Spring  │ ◄────────────────► │ Keycloak │
│ Frontend │     X-XSRF-TOKEN    │  Boot    │   (server-side)    │   IdP    │
│ (no JWT) │ ◄─── 204 / JSON ─── │ OAuth2   │                    │          │
└──────────┘                     │  Client  │                    └──────────┘
                                 └──────────┘
                                     │
                        tokens kept in HTTP session
                        (Spring Session over Redis,
                          optional, for horizontal scale)
```

- **Keycloak client**: `poc-fintech-bff-server` (confidential, PKCE S256,
  `client_secret_post`). Override the dev secret via
  `KEYCLOAK_BFF_CLIENT_SECRET`.
- **Activation**: `SPRING_PROFILES_ACTIVE=bff` (or
  `--build-arg SPRING_PROFILES_ACTIVE=bff` baked into the image).
- **Token storage**: server-side `HttpSession` only — the access and refresh
  tokens **never reach the browser**. Mitigates XSS token theft
  (NIST SP 800-63B §5.2.10, IETF `draft-ietf-oauth-browser-based-apps`).
- **Browser credential**: `__Host-SESSION` cookie —
  `HttpOnly` + `Secure` + `SameSite=Strict` + `Path=/` + no `Domain`.
  The `__Host-` prefix is enforced by the browser (RFC 6265bis §4.1.3.2).
- **Idle timeout**: 15 min (`server.servlet.session.timeout`) — PCI DSS §8.2.8.
- **CSRF**: mandatory — double-submit `__Host-XSRF-TOKEN` cookie; SPA echoes it
  in `X-XSRF-TOKEN` on every mutating request
  ([`bffClient.ts`](poc-fintech-frontend/src/api/bffClient.ts) sets
  `credentials: 'same-origin'`, `mode: 'same-origin'`, `redirect: 'manual'`).
- **BFF endpoints**:
  - `GET /bff/user` — non-sensitive identity projection (subject, username, roles, admin flag).
  - `POST /bff/logout` — invalidates session, clears `__Host-SESSION` + `__Host-XSRF-TOKEN`,
    triggers RP-initiated logout at Keycloak.
  - `GET /bff/public/csrf` — bootstraps the CSRF cookie on first SPA load.
- **401 handling**: BFF returns HTTP 401 JSON instead of a 302 (browsers cannot
  follow cross-origin auth redirects from `fetch`); SPA performs a top-level
  navigation to `/oauth2/authorization/keycloak`.
- **Distributed sessions**: `BFF_SESSION_STORE=redis` activates Spring Session
  over Redis ([`BffRedisSessionConfig`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/BffRedisSessionConfig.java))
  for horizontal scale and zero-downtime rolling restarts.
- **Filter chain**: [`BffSecurityConfig`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/BffSecurityConfig.java) (active when `bff` profile is **on**).

### Controls shared by both variants

- **No public API endpoints** — every `/api/**` route is `@PreAuthorize("hasRole('USER')")` (class-level on each controller).
- **User ID derived from the IdP** — owner of accounts / initiator of transfers is never accepted from the request body (NIST IA-2, OWASP IDOR).
- **PKCE (S256)** — prevents authorization-code interception in both flows.
- **Ultra-strict JWT validation** — alg pinning (`RS256`,`PS256`), issuer, audience, `azp`, `typ`, required claims, bounded clock skew (see [`JwtValidators`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/JwtValidators.java)).
- **Hardened response headers** — HSTS, strict CSP, COOP/COEP/CORP, Permissions-Policy (26 features denied), centralised in [`SecurityHeaders`](poc-fintech-infrastructure/src/main/java/com/mariosmant/fintech/infrastructure/security/SecurityHeaders.java) so both filter chains apply the same set.
- **Brute-force protection** — Keycloak realm: lockout after 5 failures, exponential back-off; password policy `length(12) + upperCase + lowerCase + digits + specialChars + notUsername + history(3)`.
- **Per-route rate limiting** — `transfers` 20/min, `accounts` 60/min, default 100/min — IETF `RateLimit-*` headers + RFC 7807 429 body.
- **IP-reputation pre-filter** — runs **before** the rate limiter (Spamhaus DROP / EDROP, fail-static).
- **Tamper-evident audit log** — DB triggers reject UPDATE/DELETE/TRUNCATE (V9), per-row HMAC-SHA256 chain (V10), key rotation (V11); `/actuator/auditchain` is `hasRole('ADMIN')`.
- **MDC correlation** — `requestId`, `traceId`, `spanId`, `userId`, `username` on every log line; sanitised against log-injection.
- **Secrets as `byte[]`/`char[]`** — never `String` (prevents heap/intern leakage).
- **Constant-time comparison** — `MessageDigest.isEqual` for HMAC / signatures.

> Full threat model, standards mapping (PCI DSS v4.0.1, NIST SP 800-53 / 800-63B,
> SOC 2, OWASP ASVS / Top 10), and known limitations live in
> [`SECURITY.md`](SECURITY.md).

## Quick Start

### 1. Start Infrastructure (includes Keycloak)

```bash
cd docker
docker compose up -d
```

Wait for Keycloak to be healthy (~30s). The `fintech` realm is auto-imported with:

- **Users**: `alice` (role `user`), `bob` (role `user`), `admin` (roles `user` + `admin`).
  Passwords: `Alice123!@#$`, `Bob123!@#$xx`, `Admin123!@#$`
- **Realm policy**: brute-force protection (lockout after 5 failures), `length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and passwordHistory(3)`
- **Clients**:
  - `poc-fintech-spa` — **public**, PKCE (S256). Used by the SPA in the default Resource-Server flow.
  - `poc-fintech-bff-server` — **confidential**, used by the backend in the `bff` profile (Authorization Code + PKCE, server-held tokens).
- **Audience mapping**: `audience` client-scope emits `aud=poc-fintech-api` so JwtValidators can enforce it.
- **Keycloak Admin**: <http://localhost:8180> (`admin`/`admin`)

### 2. Build & Run Backend

```bash
mvn clean install -DskipTests
mvn -pl poc-fintech-boot spring-boot:run
```

#### Optional — BFF (server-held tokens, `__Host-` cookies)

The default backend runs as an OAuth2 Resource Server — the SPA holds the Bearer token. To run with the BFF variant (backend holds tokens in `__Host-` session cookies):

```bash
# `bff` profile alone is enough locally. Override KEYCLOAK_BFF_CLIENT_SECRET
# (env var or -D system property) in non-dev environments.
#
# NOTE: the `-D…` arg is QUOTED — PowerShell's native-command parser
# otherwise splits on the `=`, leaving Maven to see ".run.profiles=bff"
# as a separate (invalid) goal. Double quotes work in PowerShell, cmd,
# bash and zsh.
mvn -pl poc-fintech-boot spring-boot:run "-Dspring-boot.run.profiles=bff"

# frontend — cross-shell via the `dev:bff` npm script (uses cross-env)
cd poc-fintech-frontend
npm install
npm run dev:bff
```

<details>
<summary>Or set env vars manually per shell</summary>

```powershell
# PowerShell
$env:SPRING_PROFILES_ACTIVE = "bff"
$env:KEYCLOAK_BFF_CLIENT_SECRET = "poc-fintech-bff-dev-secret-change-me"
mvn -pl poc-fintech-boot spring-boot:run

cd poc-fintech-frontend
$env:VITE_AUTH_MODE = "bff"; npm install; npm run dev
```

```bash
# bash / zsh
SPRING_PROFILES_ACTIVE=bff \
KEYCLOAK_BFF_CLIENT_SECRET=poc-fintech-bff-dev-secret-change-me \
  mvn -pl poc-fintech-boot spring-boot:run

cd poc-fintech-frontend
VITE_AUTH_MODE=bff npm install && VITE_AUTH_MODE=bff npm run dev
```

```cmd
:: Windows cmd.exe
set SPRING_PROFILES_ACTIVE=bff
set KEYCLOAK_BFF_CLIENT_SECRET=poc-fintech-bff-dev-secret-change-me
mvn -pl poc-fintech-boot spring-boot:run
```

</details>

### 3. Start Frontend (React)

```bash
cd poc-fintech-frontend
npm install
npm run dev
```

### 4. Test the API

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/fintech/protocol/openid-connect/token \
  -d "client_id=poc-fintech-spa" \
  -d "username=alice" \
  -d "password=Alice123!@#$" \
  -d "grant_type=password" | jq -r '.access_token')

# Create account (owner set from JWT, not from request body)
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currency":"USD","initialBalance":5000}'

# Initiate transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId":"<source-uuid>",
    "targetAccountId":"<target-uuid>",
    "amount":500,
    "sourceCurrency":"USD",
    "targetCurrency":"EUR",
    "idempotencyKey":"unique-key-001"
  }'
```

### 5. Observability

- **Frontend**: <http://localhost:5173>
- **Keycloak Admin**: <http://localhost:8180> (admin/admin)
- **Swagger UI**: <http://localhost:8080/swagger-ui.html>
- **Kafka UI**: <http://localhost:8081>
- **Prometheus**: <http://localhost:9090>
- **Grafana**: <http://localhost:3000> (admin/admin) — pre-provisioned `poc-fintech` dashboard
- **OTel Collector**: OTLP gRPC `:4317`, OTLP HTTP `:4318`, Prom exposition `:8889`
- **Actuator**: <http://localhost:8080/actuator/health>, `/actuator/prometheus`, `/actuator/info`, `/actuator/auditchain`

### 6. Run Tests

```bash
# Backend unit tests (no Docker required)
mvn test

# Backend integration + E2E tests (requires Docker for Testcontainers)
mvn verify

# Frontend tests
cd poc-fintech-frontend && npm test

# PCI DSS §6.3.1 / §6.3.3 — SCA (fails on CVSS ≥ 7.0)
mvn -Ppci-scan verify
```

### 7. Build Container Image (two variants)

The hardened distroless `Dockerfile` accepts a build-time `SPRING_PROFILES_ACTIVE` arg that is baked into the image — choose the auth flavour at build time without keeping two Dockerfiles in sync.

```bash
# Variant A — OAuth2 Resource Server (SPA holds the Bearer token, default):
docker build -t poc-fintech:latest .
docker build -t poc-fintech:resource-server --build-arg APP_VARIANT=resource-server .

# Variant B — BFF (server-held tokens, __Host-SESSION cookie):
docker build -t poc-fintech:bff \
  --build-arg SPRING_PROFILES_ACTIVE=bff \
  --build-arg APP_VARIANT=bff .

# Both variants in one command (multi-arch, optional registry push):
docker buildx bake bff                               # only the bff variant
REGISTRY=ghcr.io/<org>/poc-fintech TAG=1.0.0 \
  docker buildx bake --push                          # tag + push both

# Run (read-only FS + tmpfs is the recommended hardened deploy posture):
docker run --rm -p 8080:8080 \
  --read-only --tmpfs /tmp \
  poc-fintech:bff
```

Verify the variant baked into the image (no shell inside distroless — inspect from the host):

```bash
docker inspect poc-fintech:bff --format '{{range .Config.Env}}{{println .}}{{end}}' | grep SPRING_PROFILES_ACTIVE
```

## Project Structure

```text
poc-fintech/
├── poc-fintech-domain/                 # Pure domain (no framework deps)
│   └── src/main/java/.../domain/
│       ├── model/                      # Aggregates (Account, Transfer, LedgerEntry)
│       │   └── vo/                     # Value objects (Money, AccountId, Currency, IdempotencyKey, …)
│       ├── event/                      # Domain events (TransferInitiated/Completed/Failed, …)
│       ├── exception/                  # DuplicateTransferException, InsufficientFundsException, …
│       ├── port/outbound/              # Port interfaces (repos, FraudDetectionPort, FxRatePort, EventPublisher)
│       └── util/                       # IbanUtil (ISO 13616 MOD-97), IbanMasking (PCI 3.3)
├── poc-fintech-application/            # Application layer (no Spring)
│   └── src/main/java/.../application/
│       ├── command/                    # CQRS commands (with userId/initiatedBy from JWT)
│       ├── dto/                        # Response DTOs (read models)
│       ├── usecase/                    # AccountUseCase, InitiateTransferUseCase, *QueryUseCase
│       ├── saga/                       # TransferSagaOrchestrator + OrphanedSagaEventException
│       ├── serialization/              # EventPayloadSerializer (outbox payload encoding)
│       ├── outbox/                     # OutboxEvent model
│       └── port/                       # OutboxRepository
├── poc-fintech-infrastructure/         # Spring adapters (Java only — no resources)
│   └── src/main/java/.../infrastructure/
│       ├── config/                     # BeanConfig, CorsConfig, JacksonConfig (Jackson 3 strict), JpaAuditingConfig
│       ├── persistence/
│       │   ├── adapter/                # Jpa{Account,Transfer,Ledger,Outbox}RepositoryAdapter
│       │   ├── entity/                 # *JpaEntity classes
│       │   ├── mapper/                 # Domain ↔ JPA mappers
│       │   ├── repository/             # SpringDataXxxRepository interfaces
│       │   └── outbox/                 # OutboxShedder (NIST SI-12 retention sweep)
│       ├── messaging/
│       │   ├── config/                 # KafkaConfig (DLQ + retry + circuit breaker)
│       │   ├── consumer/               # TransferSagaEventConsumer, DeadLetterQueueConsumer
│       │   ├── dlq/                    # DeadLetterEntity, DeadLetterRepository
│       │   └── publisher/              # OutboxPollingPublisher (FOR UPDATE SKIP LOCKED)
│       ├── observability/              # TransferMetrics (Micrometer)
│       ├── web/
│       │   ├── controller/             # AccountController, TransferController, LedgerController, BffController, RootController
│       │   ├── dto/                    # Request DTOs with Bean Validation
│       │   └── exception/              # GlobalExceptionHandler + ProblemDetails (RFC 7807)
│       ├── fraud/                      # FraudDetectionAdapter (Resilience4j-wrapped)
│       ├── fx/                         # FxRateAdapter (USD-triangulated)
│       └── security/
│           ├── SecurityConfig.java        # Resource-Server filter chain + headers
│           ├── BffSecurityConfig.java     # OAuth2 Login + CSRF double-submit (`bff` profile)
│           ├── BffRedisSessionConfig.java # Spring Session over Redis (BFF horizontal scale)
│           ├── JwtDecoderConfig.java      # NimbusJwtDecoder + JwtValidators chain
│           ├── JwtValidators.java         # alg pinning · iss · aud · azp · typ · skew
│           ├── KeycloakJwtAuthoritiesConverter.java
│           ├── SecurityHeaders.java       # CSP, COOP/COEP/CORP, Permissions-Policy
│           ├── SecurityContextUtil.java
│           ├── MdcLoggingFilter.java      # requestId/traceId/userId/username MDC
│           ├── RateLimitFilter.java
│           ├── HashingUtil.java           # SHA3-256 (NIST FIPS 202)
│           ├── SecureSecretUtils.java     # HMAC, byte[]/char[] secrets, CSPRNG
│           ├── audit/                  # @Audited, AuditAspect, AuditChain{Writer,Verifier,Hasher,KeyRing,Endpoint}
│           ├── ratelimit/              # RateLimiter port + Caffeine/Bucket4jRedis adapters + CircuitBreakingRateLimiter
│           ├── reputation/             # BlockedIpFilter + IpReputationService (Spamhaus DROP/EDROP refresher)
│           └── tenant/                 # TenantResolver + JwtClaimTenantResolver
├── poc-fintech-boot/                   # Spring Boot entry point + config + tests
│   ├── src/main/java/.../              # FintechApplication, OpenApiConfig, StartupBannerListener
│   ├── src/main/resources/
│   │   ├── application.yml             # Profiles: default · bff · json (ECS structured logs)
│   │   └── db/migration/               # Flyway V1–V11 SQL migrations
│   └── src/test/                       # Unit, integration, E2E (Testcontainers), ArchUnit
│       ├── java/.../arch/              # HexagonalArchitectureTest (fitness functions)
│       ├── java/.../e2e/               # TransferE2ETest
│       ├── java/.../integration/       # AuditChainIntegrationTest, TransferIntegrationTest
│       ├── java/.../infrastructure/    # JacksonConfigIntegrationTest, BffControllerTest, BffSecurityConfigSmokeTest, SecurityHeadersIntegrationTest
│       ├── java/.../testcontainers/    # TestcontainersConfig + EnabledIfDockerAvailable + TestSecurityConfig
│       └── resources/application-test.yml
├── poc-fintech-frontend/               # React 19 + TypeScript + Vite
│   ├── src/
│   │   ├── auth/                       # Two providers: AuthProvider (keycloak-js) + BffAuthProvider (cookie session); loginGuard
│   │   ├── api/                        # client.ts (Bearer token) + bffClient.ts (same-origin + double-submit CSRF)
│   │   ├── components/
│   │   │   ├── layout/                 # AppLayout (header, nav, logout)
│   │   │   └── ui/                     # IbanDisplay (grouped + copy), StatusBadge, Feedback toast
│   │   ├── features/                   # Dashboard, Accounts, Transfers, Ledger pages
│   │   ├── hooks/                      # useApi (React Query)
│   │   ├── types/                      # TypeScript API types incl. RFC 7807 ProblemDetail
│   │   └── utils/                      # format.ts, iban.ts (client-side IBAN formatting)
│   ├── package.json                    # `dev` / `dev:bff`, `test` (Vitest), `build` / `build:bff`
│   └── vite.config.ts                  # Vite + dev proxy for /api, /bff, /oauth2, /login/oauth2, /logout, /realms
├── docker/                             # Compose stack + observability
│   ├── docker-compose.yml              # Postgres, Keycloak (+ own Postgres), Kafka (KRaft), Kafka UI, Prometheus, Grafana, OTel Collector
│   ├── keycloak/fintech-realm.json     # Pre-configured realm, clients, scopes, users, roles, audience mapper
│   ├── prometheus/prometheus.yml
│   ├── grafana/                        # Pre-provisioned dashboard + datasource
│   └── otel-collector/                 # OTLP receivers + tail-based sampling + Prom exporter
├── Dockerfile                          # Multi-stage distroless (builder → layertools → gcr.io/distroless/java25)
├── docker-bake.hcl                     # `bake resource-server` / `bake bff` — multi-arch, push-ready
└── pom.xml                             # Parent POM; `pci-scan` profile = OWASP Dependency-Check
```

## Database Schema (Flyway Migrations)

| Migration | Table / Change | Purpose |
| --- | --- | --- |
| V1 | `accounts` | Financial accounts with optimistic locking |
| V2 | `transfers` | Transfer lifecycle with saga state |
| V3 | `ledger_entries` | Double-entry accounting (immutable) |
| V4 | `outbox_events` | Transactional outbox for Kafka |
| V5 | `audit_log` | Security audit trail (NIST AU-2) |
| V6 | `accounts.owner_id`, `transfers.initiated_by` | JWT user binding |
| V7 | `dead_letter_queue` | Failed Kafka messages for manual replay |
| V8 | `accounts.iban` | ISO 13616 IBAN column + unique index |
| V9 | `audit_log` triggers | PCI DSS §10.3.4 / §10.5.2 — UPDATE/DELETE/TRUNCATE rejected at DB level |
| V10 | `audit_log` HMAC chain + `audit_log_head` | PCI DSS §10.5 / NIST AU-9(3) — per-row `prev_hash`, `row_hash`, `chain_seq` for tamper evidence |
| V11 | `audit_log.key_id` + `active_key_id` | HMAC key rotation |

## Further Documentation

| Document                       | Purpose                                                           |
| ------------------------------ | ----------------------------------------------------------------- |
| [`SECURITY.md`](./SECURITY.md) | Threat model, token handling, scope, reporting, known limitations |
