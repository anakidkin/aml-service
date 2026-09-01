# Project Roadmap & Future Improvements

This document tracks planned technical enhancements, performance optimizations, and infrastructure tasks.

### Architecture & Service Separation

- [ ] **Service Split (Engine vs Worker):** Split the monolith into `aml-engine` (hot REST/Kafka API with < 100ms SLA)
  and `aml-worker` (async CDC consumer for Cassandra).
- [ ] **Gradle Multi-Module Setup:** Restructure project into `aml-common`, `aml-engine`, and `aml-worker` modules.

---

### Compliance & Data Lifecycle

- [ ] **LocalStack (AWS S3 Integration):** Add LocalStack to `docker-compose.yml` for local S3 emulation.
- [ ] **Kafka S3 Sink & Glacier Policy:** Stream Kafka events to S3 with a 30-day lifecycle rule to transition data to
  S3 Glacier for 7-year regulatory retention.

---

### Integration Testing & Race Conditions

- [X] **Async History Race Condition Test:** Write a test simulating rapid consecutive transactions (e.g., 2
  transactions within 10ms for the same account).
    - *Problem:* Because history persistence to Cassandra/DB is asynchronous, the 2nd transaction evaluates against a
      stale state (before the 1st is recorded) and passes, bypassing daily amount limits.
    - *Goal:* Expose this race condition with a failing test and implement proper state locking/state consistency
      mechanisms.

---

### Performance & SLA (Gatling & Kafka Engine)

- [ ] **Gatling Load SLA Tuning:** Optimize the processing pipeline to pass Gatling SLA checks.
- [X] **Kafka Listener Load Tests & Metrics:** Add performance tests and SLA metrics for the existing Kafka listener.
  Currently, Gatling only benchmarks the REST endpoint, leaving the asynchronous Kafka processing pipeline unmeasured.
- [X] **Switch to Virtual Threads:** Spring Boot & Kafka listeners configured to run on Virtual Threads.
- [ ] **GC & Allocation Profiling:** Address high object allocation rates identified in JMH benchmarks.
- [X] **Redis / Valkey Integration & Distributed Locking:** Utilize Redis for caching, hot counters, and distributed locking to
  prevent race conditions.
- [X] **Adopt Debezium CDC for Outbox Pattern**

---

### Resilience & Fault Tolerance

- [ ] **Resilience4j Integration:** Implement resilience patterns across external integration points and data layers

---

### Developer Experience

- [X] linters
- [X] static code analysis
- [X] CI/CD
- [X] Dockerfile
