# Project Roadmap & Future Improvements

This document tracks planned technical enhancements, performance optimizations, and infrastructure tasks.

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
- [X] Switch to the Virtual Threads
- [ ] **GC & Allocation Profiling:** Address high object allocation rates identified in JMH benchmarks.
- [X] **Redis / Valkey Integration:** Utilize the provisioned Redis/Valkey container and dependencies to cache
  frequently accessed data.
- [X] Adopt Debezium CDC for Outbox Pattern

---

### Resilience & Fault Tolerance

- [ ] **Resilience4j Integration:** Implement resilience patterns across external integration points and data layers

### Developer Experience

- [ ] linters
- [ ] static code analysis
- [ ] CI/CD
- [ ] Dockerfile
