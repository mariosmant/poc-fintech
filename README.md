# POC Fintech

> Production-grade fintech Proof of Concept — Java 25, Spring Boot 4.0.5, Jackson 3.x (tools.jackson), React 19 + TypeScript, PostgreSQL, Kafka, Keycloak OAuth2/OIDC

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React 19 + TypeScript + Vite + TailwindCSS)      │
│  (Dashboard, Accounts, Transfers, Ledger, React Query)      │
│  (Keycloak JS OIDC auth, PKCE, protected routes)           │
├─────────────────────────────────────────────────────────────┤
│                     Boot Module                              │
│  (Spring Boot entry point, config, Docker, tests)           │
├─────────────────────────────────────────────────────────────┤
│                Infrastructure Module                         │
│  (Spring adapters: JPA, Kafka, REST, Security, Observ.)     │
│  (OAuth2 Resource Server, Audit AOP, MDC Logging, DLQ)      │
├─────────────────────────────────────────────────────────────┤
│                Application Module                            │
│  (CQRS handlers, Saga Orchestrator, Use Cases, DTOs)        │
├─────────────────────────────────────────────────────────────┤
│                  Domain Module                               │
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
| **OAuth2/OIDC (Keycloak)** | JWT-based authentication, PKCE, realm roles, BFF pattern |
| **NIST/SOG-IS Security** | SHA3-256 hashing, HMAC-SHA256, secrets as `byte[]`/`char[]`, HSTS, CSP, constant-time comparison |
| **Audit Trail (AOP)** | `@Audited` aspect → `audit_log` table with user ID, IP, action, timestamps (NIST AU-2) |
| **MDC Structured Logging** | userId, requestId, traceId on every log line (SLF4J2) |
| **Rate Limiting** | Per-user rate limiting with 429 Too Many Requests + Retry-After headers |
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
│          │ ────────────────────────────────────────────────► │ Resource │
│          │   fetch + Bearer token                           │  Server  │
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

### 3. Start Frontend (React)

```bash
cd poc-fintech-frontend
npm install
npm run dev
```

Frontend at http://localhost:5173 — will redirect to Keycloak login automatically.

### 4. API Examples (with JWT)

```bash
# Get a token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8180/realms/fintech/protocol/openid-connect/token \
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
```

## Module Structure

```
poc-fintech/
├── pom.xml                          # Parent POM (aggregator)
├── poc-fintech-domain/              # Pure domain model (no Spring)
│   └── src/main/java/.../domain/
│       ├── model/                   # Aggregates (Account, Transfer, LedgerEntry)
│       │   └── vo/                  # Value Objects (Money, AccountId, Currency...)
│       ├── event/                   # Domain Events (sealed interface hierarchy)
│       ├── exception/               # Domain exceptions
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
│       │   ├── SecurityContextUtil.java # JWT user extraction
│       │   ├── MdcLoggingFilter.java # MDC userId/requestId/traceId
│       │   └── RateLimitFilter.java  # Per-user rate limiting
│       ├── observability/           # Micrometer metrics
│       └── config/                  # Bean wiring, JPA auditing, CORS, Jackson
├── poc-fintech-boot/                # Spring Boot entry point
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
