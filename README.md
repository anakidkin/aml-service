# AML Service — High-Throughput Fraud & AML Detection Engine

[![CI/CD Pipeline](https://github.com/anakidkin/aml-service/actions/workflows/ci.yml/badge.svg)](https://github.com/anakidkin/aml-service/actions/workflows/ci.yml)
[![Docker Image](https://img.shields.io/badge/ghcr.io-aml--service-blue?logo=docker&logoColor=white)](https://github.com/anakidkin/aml-service/pkgs/container/aml-service)
[![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

Real-time Anti-Money Laundering (AML) & Fraud Detection engine designed for high-throughput banking systems. The service
evaluates financial transactions against customizable compliance and risk rules within strict SLA targets.

---

## Key Architectural Requirements & SLA

* **Target Throughput:** 10,000 Transactions Per Second (TPS).
* **Latency SLA:** `< 100 ms` End-to-End decision pipeline (Targeting ~40–60 ms p99).
* **Audit & Regulatory Compliance:** Guaranteed immutable audit trail and historical aggregation (Transactional Outbox +
  Apache Cassandra).
* **Availability & Resilience:** Zero data-loss architecture (**At-Least-Once** event delivery via Outbox Pattern &
  Kafka).
* Long-Term Audit Retention (Compliance): 7-year immutable historical audit storage.

---

```mermaid
flowchart TD
subgraph Ingress [Ingress Layer]
ClientREST([💳 Payment Gateway / REST])
KafkaIngress{{📥 External Kafka Topic}}
end

subgraph EngineService ["AML Engine API (Stateless & Fast SLA less than 100ms)"]
AppEngine[⚡ AML Engine API]

AppEngine <-->|3. Acquire Lock & Sync Incr Hot Counters| Redis[(🔴 Redis Cluster\nDistributed Lock & Hot State)]
AppEngine <-->|4. Fetch Historical Profile| Cassandra[(⚡ Apache Cassandra\nWarm Aggregates)]
AppEngine -->|5. Evaluate Rules & Save Tx + Outbox| Postgres[(🐘 PostgreSQL\nHot Tier: Purge 30d)]
end

ClientREST -->|1. REST Request| AppEngine
KafkaIngress -->|2. Consume Event| AppEngine
AppEngine -->|6. Sync Decision Response| ClientREST

subgraph CDC_Pipeline [CDC & Event Bus]
Postgres -->|7. WAL Capture| Debezium[🔌 Debezium CDC]
Debezium -->|8. Publish Verdict Events| KafkaInternal{{🚀 Apache Kafka KRaft}}
end

subgraph WorkerService [AML Aggregator Worker]
KafkaInternal -->|9. Consume Internal Events| AppWorker[⚙️ AML Worker Service]
AppWorker -->|10. Async Update Historical Aggregates| Cassandra
end

subgraph ColdArchive [Long-Term Audit Tier]
KafkaInternal -->|11. S3 Sink Connector & Lifecycle| Glacier[🧊 AWS S3 Glacier Deep Archive\n7-Year Compliance]
end

classDef primary fill:#2563eb,stroke:#1e40af,color:#fff
classDef worker fill:#4f46e5,stroke:#3730a3,color:#fff
classDef storage fill:#059669,stroke:#047857,color:#fff
classDef redis fill:#dc2626,stroke:#991b1b,color:#fff
classDef coldStorage fill:#0284c7,stroke:#0369a1,color:#fff
classDef kafka fill:#d97706,stroke:#b45309,color:#fff

class AppEngine primary
class AppWorker worker
class Postgres,Cassandra storage
class Redis redis
class KafkaIngress,KafkaInternal kafka
class Glacier coldStorage
```

### Data Retention & Storage Tiers

To support 10k TPS while keeping operational storage lightweight and meeting strict SLA targets:

* **PostgreSQL (Hot Data):** Retains primary transactions & outbox logs for **30 days** before automated purging.
* **Apache Cassandra (Warm Metrics):** Stores 24h / 30d rolling aggregates using native **TTL (30-90 days)** for instant
  SLA-compliant risk evaluation.
* **AWS S3 & Glacier (Cold Storage / Audit):** Kafka streams immutable events to **S3**, automatically transitioning to
  **Glacier Deep Archive** after 30 days for cost-effective **7-year regulatory compliance**.

## Latency Budget Breakdown (< 100 ms)

| Step                    | Operation                              | Target Latency     | Tech Stack                     |
|-------------------------|----------------------------------------|--------------------|--------------------------------|
| **1. Ingestion**        | REST Request ingestion & mapping       | ~5–10 ms           | Spring Web / MapStruct         |
| **2. Context Fetch**    | Aggregation metrics lookup (24h / 30d) | ~10–15 ms          | Apache Cassandra               |
| **3. Rule Evaluation**  | In-memory rules execution pipeline     | ~5–10 ms           | Pure Java Domain Rules         |
| **4. Persistence**      | Transaction + Outbox Event atomic save | ~10–15 ms          | PostgreSQL (Transactional)     |
| **5. Asynchronous Pub** | Outbox polling & Kafka verdict publish | Async (Background) | Kafka Producer / Outbox Poller |
| **Total**               | **End-to-End Synchronous Decision**    | **~30–50 ms**      | **(Well under 100ms SLA)**     |

---

## Tech Stack & Infrastructure

* **Java 25** + **Spring Boot 4**
* **PostgreSQL** (Transaction storage & Transactional Outbox)
* **Apache Cassandra** (NoSQL high-speed transaction history & aggregated account metrics)
* **Apache Kafka (KRaft mode)** (High-throughput message streaming without ZooKeeper)
* **AWS S3 / S3 Glacier** (Cost-effective 7-year audit retention pipeline via lifecycle policies)
* **Debezium** (CDC outbox event streaming)
* **Redis / Valkey** (High-speed caching layer)
* **Testcontainers & AssertJ** (Integration testing with real dependencies)

---

## Quick Start (Local Setup)

### Prerequisites

* Docker Engine 24+ & `docker compose` CLI plugin
* Java 25+ JDK
* Gradle 8.x (or Gradle Wrapper included)
* `pre-commit` CLI (recommended for local contributors)

### 1. Code Quality & Pre-commit Hooks Setup

The project enforces formatting (Spotless), secret scanning (Gitleaks), and syntax validation via Git pre-commit hooks.

1. **Install `pre-commit` CLI on your system (one-time setup):**
    * **macOS:** `brew install pre-commit gitleaks`
    * **Linux:** `sudo apt install pre-commit` (or `pip install pre-commit`)
    * **Windows:** `winget install pre-commit.pre-commit` (or `pip install pre-commit`)

2. **Initialize hooks:**
   Compiling the project via `./gradlew compileJava` will automatically bind the pre-commit hooks if the CLI is present
   on your system.

* **Run all quality checks manually:**
  ```bash
  pre-commit run --all-files

```

* **Emergency bypass (if needed):**
```bash
git commit -m "hotfix: urgent change" --no-verify

```

### 2. Start Infrastructure Dependencies

```bash
docker compose up -d
```

*This spins up PostgreSQL, Apache Cassandra, Apache Kafka (KRaft), Debezium, and Valkey.*

### 3. Build and Run Application

```bash
./gradlew bootRun

```

### 4. Run Test Suite

```bash
./gradlew test
./gradlew integrationTest
./gradlew jmh
./gradlew gatlingRun
```

### 5. Send Test Transaction for Evaluation

```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountFrom": "ACC-100200",
    "accountTo": "ACC-900800",
    "amount": 15000.00,
    "currency": "USD",
    "mccCode": "5411",
    "isP2p": false
  }'
```

## Roadmap

Check [ROADMAP.md](./ROADMAP.md) for planned performance optimizations, memory profiling tasks, and infrastructure
enhancements.