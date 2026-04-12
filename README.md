# POC Fintech

> Production-grade fintech Proof of Concept — Java 25, Spring Boot 4.0.5, Jackson 3.x (tools.jackson), React 19 + TypeScript, PostgreSQL, Kafka

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React 19 + TypeScript + Vite + TailwindCSS)      │
│  (Dashboard, Accounts, Transfers, Ledger, React Query)      │
├─────────────────────────────────────────────────────────────┤
│                     Boot Module                              │
│  (Spring Boot entry point, config, Docker, tests)           │
├─────────────────────────────────────────────────────────────┤
│                Infrastructure Module                         │
│  (Spring adapters: JPA, Kafka, REST, Security, Observ.)     │
├─────────────────────────────────────────────────────────────┤
│                Application Module                            │
│  (CQRS handlers, Saga Orchestrator, Use Cases, DTOs)        │
├─────────────────────────────────────────────────────────────┤
│                  Domain Module                               │
│  (Aggregates, Value Objects, Events, Ports — pure Java)     │
└─────────────────────────────────────────────────────────────┘
```

## What This POC Demonstrates

| Pattern / Practice | Implementation |
|----|---|
| **Hexagonal Architecture** | Domain ports (interfaces) + Infrastructure adapters |
| **DDD** | Aggregates (`Account`, `Transfer`), Value Objects (`Money`, `AccountId`), Domain Events |
| **CQRS** | Command handlers (write) / Query handlers (read) split |
| **Saga Orchestrator** | `TransferSagaOrchestrator` — multi-step transfer flow |
| **Transactional Outbox** | Events written to `outbox_events` table atomically with state changes |
| **Exactly-Once Processing** | Outbox + idempotent consumer (idempotency keys) |
| **Event-Driven (Kafka)** | `OutboxPollingPublisher` → Kafka → `TransferSagaEventConsumer` |
| **Optimistic Locking** | JPA `@Version` — no pessimistic locks |
| **Double-Entry Ledger** | `LedgerEntry` — balanced debit/credit accounting |
| **Multi-Currency FX** | `FxRateAdapter` — rate triangulation through USD |
| **Fraud Detection** | `FraudDetectionAdapter` — rule-based with circuit breaker |
| **Circuit Breaker + Retry** | Resilience4j with exponential backoff |
| **NIST/SOGIS Security** | SHA3-256 hashing, HSTS, stateless sessions, input validation |
| **Observability** | Micrometer + Prometheus + Grafana + OpenTelemetry tracing |
| **OpenAPI** | Springdoc auto-generated Swagger UI |

## Tech Stack

| Technology | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.5 |
| Spring Framework | 7.0.6 |
| Jackson | 3.x (tools.jackson) |
| React | 19 + TypeScript 5.7 |
| Vite | 6 |
| TailwindCSS | 3.4 |
| React Query | 5 (TanStack) |
| PostgreSQL | 16 |
| Apache Kafka | 7.6+ (KRaft, no Confluent) |
| Kafka UI | Latest |
| Flyway | 11.14 |
| Resilience4j | 2.3.0 |
| Testcontainers | 2.x |
| Micrometer + Prometheus | Metrics |
| Grafana | Dashboards |
| Springdoc OpenAPI | 2.8.6 |
| Vitest | 2.1 (frontend tests) |

## Quick Start

### 1. Start Infrastructure

```bash
cd docker
docker compose up -d
```

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

Frontend available at http://localhost:5173 (proxies API calls to backend on :8080).

### 4. API Examples

```bash
# Create source account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Alice","currency":"USD","initialBalance":5000}'

# Create target account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Bob","currency":"EUR","initialBalance":1000}'

# Initiate transfer
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId":"<source-uuid>",
    "targetAccountId":"<target-uuid>",
    "amount":500,
    "sourceCurrency":"USD",
    "targetCurrency":"EUR",
    "idempotencyKey":"unique-key-001"
  }'

# Query transfer
curl http://localhost:8080/api/v1/transfers/<transfer-uuid>

# Query ledger
curl http://localhost:8080/api/v1/ledger/account/<account-uuid>
```

### 5. Observability

- **Frontend**: http://localhost:5173
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Kafka UI**: http://localhost:8080 (view topics, messages, consumer groups)
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
│       ├── command/                 # CQRS commands
│       ├── dto/                     # Response DTOs (read models)
│       ├── usecase/                 # Use case handlers
│       ├── saga/                    # Saga orchestrator
│       └── outbox/                  # Outbox event model
├── poc-fintech-infrastructure/      # Spring adapters
│   └── src/main/java/.../infrastructure/
│       ├── persistence/             # JPA entities, mappers, repos, adapters
│       ├── messaging/               # Kafka config, outbox publisher
│       ├── web/                     # REST controllers, exception handler
│       ├── fraud/                   # Fraud detection adapter
│       ├── fx/                      # FX rate adapter
│       ├── security/                # Security config, SHA3-256 hashing
│       ├── observability/           # Micrometer metrics
│       └── config/                  # Bean wiring, JPA auditing, CORS, Jackson
├── poc-fintech-boot/                # Spring Boot entry point
│   ├── src/main/resources/
│   │   ├── application.yml          # App config
│   │   └── db/migration/            # Flyway SQL migrations (V1-V4)
│   └── src/test/java/               # Integration & E2E tests + Testcontainers
├── poc-fintech-frontend/            # React 19 + TypeScript + Vite
│   ├── src/
│   │   ├── api/                     # API client (typed fetch wrapper)
│   │   ├── components/              # UI components (layout, StatusBadge, Feedback)
│   │   ├── features/                # Feature pages (Dashboard, Accounts, Transfers, Ledger)
│   │   ├── hooks/                   # React Query hooks (useApi)
│   │   ├── types/                   # TypeScript API types
│   │   └── utils/                   # Formatting, idempotency key generation
│   ├── package.json
│   └── vite.config.ts               # Vite + Vitest + API proxy config
└── docker/                          # Docker Compose + observability
    ├── docker-compose.yml           # Postgres, Kafka (KRaft), Prometheus, Grafana
    ├── prometheus/prometheus.yml
    └── grafana/
        ├── dashboards/              # Pre-provisioned Grafana dashboard JSON
        └── provisioning/            # Datasource + dashboard provisioning
```

