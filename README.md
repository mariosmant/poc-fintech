# POC Fintech

> Production-grade fintech Proof of Concept — Java 25, Spring Boot 4.0.5, Jackson 3.x (tools.jackson), React 19 + TypeScript, PostgreSQL, Kafka, Keycloak OAuth2/OIDC

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
```

## What This POC Demonstrates

| Pattern / Practice | Implementation |
|----|---|
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
| **Supply Chain & Container** | Distroless multi-stage Dockerfile (UID 65532, no shell) |
| **Observability** | Micrometer + Prometheus + Grafana + OpenTelemetry tracing |
| **OpenAPI** | Springdoc auto-generated Swagger UI with JWT security scheme |

## Tech Stack

| Technology | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.5 |
| Spring Framework | 7.0.6 |
| Jackson | 3.x (tools.jackson) |
| Keycloak | Latest (OIDC/OAuth2 IdP) |
| React | 19 + TypeScript 5.7 |
| Vite | 6 |
| TailwindCSS | 3.4 |
| React Query | 5 (TanStack) |
| keycloak-js | Latest |
| PostgreSQL | 18 |
| Apache Kafka | Latest (KRaft, no Confluent) |
| Kafka UI | Latest |
| Flyway | 11.14 |
| Resilience4j | 2.3.0 |
| Testcontainers | 2.x |
| Micrometer + Prometheus | Metrics |
| Grafana | Dashboards |
| Springdoc OpenAPI | 2.8.6 |
| Vitest | 2.1 (frontend tests) |

## Security Architecture

```
┌──────────┐    PKCE/OIDC     ┌──────────┐     JWT Bearer     ┌──────────┐
│  React   │ ◄──────────────► │ Keycloak │                    │  Spring  │
│ Frontend │                  │   IdP    │                    │  Boot    │
│          │ ───────────────────────────────────────────────► │ Resource │
│          │   fetch + Bearer token      |                    │  Server  │
└──────────┘                  └──────────┘                    └──────────┘
                                                                   │
                                                          JWT validated via
                                                          JWK Set endpoint
```

- **No public API endpoints** — all `/api/**` routes require valid JWT
- **User ID from JWT only** — account/transfer ownership never from client input
- **PKCE (S256)** — prevents authorization code interception
- **Brute force protection** — Keycloak lockout after 5 failed attempts
- **Rate limiting** — per-user 100 req/min with 429 + Retry-After
- **Audit trail** — all critical actions logged to `audit_log` table
- **Secrets as byte[]/char[]** — never as String (prevents heap/intern leakage)
- **Constant-time comparison** — prevents timing attacks on HMAC/signatures

## Quick Start

### 1. Start Infrastructure (includes Keycloak)

```bash
cd docker
docker compose up -d
```

Wait for Keycloak to be healthy (~30s). The `fintech` realm is auto-imported with:
- **Users**: `alice` / `Alice123!@#$`, `bob` / `Bob123!@#$xx`, `admin` / `Admin123!@#$`
- **Client**: `poc-fintech-bff` (public, PKCE)
- **Keycloak Admin**: http://localhost:8180 (`admin`/`admin`)

### 2. Build & Run Backend

```bash
mvn clean install -DskipTests
mvn -pl poc-fintech-boot spring-boot:run
```

#### Optional — BFF (server-held tokens, `__Host-` cookies)

The default backend runs with OAuth2 Resource Server — SPA holds the Bearer
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
mvn -pl poc-fintech-boot spring-boot:run -Dspring-boot.run.profiles=bff
  -d "client_id=poc-fintech-bff" \
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

- **Frontend**: http://localhost:5173
- **Keycloak Admin**: http://localhost:8180 (admin/admin)
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Kafka UI**: http://localhost:8081
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Actuator**: http://localhost:8080/actuator/health

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

The hardened distroless `Dockerfile` accepts a build-time
`SPRING_PROFILES_ACTIVE` arg that is baked into the image — choose the auth
flavour at build time without keeping two Dockerfiles in sync.

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

Verify the variant from outside the image (no shell inside distroless):

```bash
The default backend runs (OAuth2 Resource Server — SPA holds the Bearer
token). To run (OAuth2 Login — the backend holds the token, browser
| **Rate Limiting** | Per-user rate limiting with 429 Too Many Requests + Retry-After headers |
│       └── port/outbound/           # Port interfaces (repositories, services)
├── poc-fintech-application/         # Application layer (no Spring)
│   └── src/main/java/.../application/
│       ├── command/                 # CQRS commands (with userId/initiatedBy)
│       ├── dto/                     # Response DTOs (read models)
│       ├── usecase/                 # Use case handlers
│       ├── saga/                    # Saga orchestrator
│       └── outbox/                  # Outbox event model
├── poc-fintech-infrastructure/      # Spring adapters
│   └── src/main/java/.../infrastructure/
│       ├── persistence/             # JPA entities, mappers, repos, adapters
│       ├── messaging/               # Kafka config, outbox publisher, DLQ consumer
│       │   ├── config/              # KafkaConfig (DLQ + retry + circuit breaker)
│       │   ├── consumer/            # TransferSagaEventConsumer, DeadLetterQueueConsumer
│       │   ├── dlq/                 # DeadLetterEntity, DeadLetterRepository
│       │   └── publisher/           # OutboxPollingPublisher
│       ├── web/                     # REST controllers, exception handler
│       ├── fraud/                   # Fraud detection adapter
│       ├── fx/                      # FX rate adapter
│       ├── security/                # SecurityConfig (OAuth2), MDC filter, rate limiting
│       │   ├── audit/               # @Audited annotation, AuditAspect, AuditLogEntity
│       │   ├── HashingUtil.java     # SHA3-256 (NIST FIPS 202)
│       │   ├── SecureSecretUtils.java # HMAC, byte[]/char[] secret handling, CSPRNG
│   ├── src/main/resources/
│   │   ├── application.yml          # App config (OAuth2, Kafka, Resilience4j)
│   │   └── db/migration/            # Flyway SQL migrations (V1-V7)
│   │       ├── V1-V4                # Core tables (accounts, transfers, ledger, outbox)
│   │       ├── V5__create_audit_log.sql
│   │       ├── V6__add_user_id_columns.sql
│   │       └── V7__create_dead_letter_queue.sql
│   └── src/test/java/               # Integration & E2E tests + Testcontainers
├── poc-fintech-frontend/            # React 19 + TypeScript + Vite
│   ├── src/
│   │   ├── auth/                    # Keycloak JS adapter + AuthProvider
│   │   ├── api/                     # API client (typed fetch + Bearer token)
│   │   ├── components/              # UI components (layout with logout, StatusBadge)
│   │   ├── features/                # Feature pages (Dashboard, Accounts, Transfers, Ledger)
│   │   ├── hooks/                   # React Query hooks (useApi)
│   │   ├── types/                   # TypeScript API types
│   │   └── utils/                   # Formatting, idempotency key generation
│   ├── package.json
│   └── vite.config.ts               # Vite + Vitest + API/Keycloak proxy
└── docker/                          # Docker Compose + observability
    ├── docker-compose.yml           # Postgres, Kafka, Keycloak, Prometheus, Grafana
    ├── keycloak/
    │   └── fintech-realm.json       # Pre-configured realm, clients, users, roles
    ├── prometheus/prometheus.yml
    └── grafana/
        ├── dashboards/              # Pre-provisioned Grafana dashboard JSON
        └── provisioning/            # Datasource + dashboard provisioning
```

## Database Schema (Flyway Migrations)

| Migration | Table | Purpose |
|---|---|---|
| V1 | `accounts` | Financial accounts with optimistic locking |
| V2 | `transfers` | Transfer lifecycle with saga state |
| V3 | `ledger_entries` | Double-entry accounting (immutable) |
| V4 | `outbox_events` | Transactional outbox for Kafka |
| V5 | `audit_log` | Security audit trail (NIST AU-2) |
| V6 | `accounts.owner_id`, `transfers.initiated_by` | JWT user binding |
| V7 | `dead_letter_queue` | Failed Kafka messages for manual replay |

## Further Documentation

| Document | Purpose |
|---|---|
| [`SECURITY.md`](./SECURITY.md) | Threat model, token handling, scope, reporting, known limitations |
| V8 | `accounts.iban` | ISO 13616 IBAN column on accounts |
| V9 | `audit_log` triggers | DB-level immutability (PCI DSS §10.3.4) |
| V10 | `audit_log` chain cols | HMAC-SHA256 tamper-evident chain |
| V11 | `audit_log.key_id` + `active_key_id` | HMAC key rotation |
| V8 | `accounts.iban` | IBAN column + unique index |
| V9 | `audit_log` triggers | PCI DSS §10.3.4 / §10.5.2 — UPDATE/DELETE/TRUNCATE rejected at DB level |
| V10 | `audit_log` HMAC chain + `audit_log_head` | PCI DSS §10.5 / NIST AU-9(3) — per-row `prev_hash`, `row_hash`, `chain_seq` for tamper evidence |

