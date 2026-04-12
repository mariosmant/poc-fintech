import { StatCard } from '../../components/ui/Feedback';

/** Dashboard page — summary overview of the fintech platform. */
export function DashboardPage() {
  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h2>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <StatCard label="Architecture" value="Hexagonal" icon="🏛️" colour="primary" />
        <StatCard label="Patterns" value="CQRS + Saga" icon="🔄" colour="success" />
        <StatCard label="Messaging" value="Kafka Outbox" icon="📨" colour="warning" />
        <StatCard label="Security" value="NIST/SOGIS" icon="🔒" colour="danger" />
      </div>

      {/* Architecture diagram */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
        <h3 className="text-lg font-semibold mb-4">Transfer Saga Flow</h3>
        <div className="flex flex-wrap items-center gap-2 text-sm font-mono">
          {[
            'INITIATED',
            '→ Fraud Check',
            '→ FX Convert',
            '→ Debit',
            '→ Credit',
            '→ Ledger',
            '→ COMPLETED',
          ].map((step) => (
            <span
              key={step}
              className="bg-primary-50 text-primary-700 px-3 py-1.5 rounded-lg border border-primary-200"
            >
              {step}
            </span>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <h3 className="text-lg font-semibold mb-3">🏗️ Architecture Patterns</h3>
          <ul className="space-y-2 text-sm text-gray-600">
            <li>✅ Hexagonal (Ports &amp; Adapters)</li>
            <li>✅ Domain-Driven Design — Aggregates, Value Objects</li>
            <li>✅ CQRS — Command / Query separation</li>
            <li>✅ Saga Orchestrator — Multi-step transfer flow</li>
            <li>✅ Transactional Outbox — Exactly-once delivery</li>
            <li>✅ Event-Driven — Kafka</li>
            <li>✅ Double-Entry Ledger</li>
            <li>✅ Multi-Currency FX</li>
          </ul>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <h3 className="text-lg font-semibold mb-3">🔧 Tech Stack</h3>
          <ul className="space-y-2 text-sm text-gray-600">
            <li>☕ Java 25 + Spring Boot 4.0.5</li>
            <li>⚛️ React 19 + TypeScript + Vite</li>
            <li>🐘 PostgreSQL 16 + Flyway</li>
            <li>📨 Apache Kafka 7.6 (KRaft mode)</li>
            <li>🛡️ Resilience4j — Circuit Breaker + Retry</li>
            <li>📊 Micrometer + Prometheus + Grafana</li>
            <li>📖 OpenAPI / Swagger UI + Kafka UI</li>
            <li>🧪 Testcontainers + JUnit 5 + Vitest</li>
          </ul>
        </div>
      </div>
    </div>
  );
}

