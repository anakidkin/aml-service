# Benchmark Analysis: REST vs Async Kafka Event-Driven Evaluation

**Date:** August 2026  
**Environment:** Single-instance Service, Valkey/Redis, PostgreSQL, Apache Kafka  
**Tooling:** Gatling 3.x, Spring Boot Actuator (Micrometer)

---

## 1. Executive Summary

A baseline load test was conducted to evaluate the service under a load of **200 RPS** (Requests/Events Per Second) over
a 30-second window. The objective was to compare synchronous REST API evaluation against asynchronous Kafka event
ingestion, validate compliance with the **< 100 ms SLA**, and identify architectural bottlenecks.

### Key Key Takeaways

1. **REST Ingestion Bottleneck:** Under 200 RPS, Tomcat thread pool exhaustion (`server.tomcat.threads.max = 200`)
   caused response times to degrade sharply to **3.8s–6.8s** (p95).
2. **Core Domain Performance:** The core domain logic (`aml.transaction.evaluation.latency`) operates within the latency
   budget at an average of **20.8 ms** (max **44.1 ms**).
3. **Kafka Ingestion Efficiency:** Kafka decoupled the client immediately (producer response time p95 = **18 ms**).
   However, a single consumer thread (`concurrency = 1`) processed ~48 msg/sec, resulting in a maximum **Consumer Lag of
   4,500 messages**.
4. **Outbox Polling Bottleneck:** The `@Scheduled(fixedDelay = 1000)` Transactional Outbox polling mechanism introduced
   an artificial **0–1000 ms delay** and capped outbox throughput at **50 TPS**, violating both the SLA and the 10,000
   TPS target.

---

## 2. Benchmark Metrics Comparison

### Gatling Ingress Load Test (200 RPS Peak)

| Metric                    | Synchronous REST API | Asynchronous Kafka Ingestion |
|:--------------------------|:---------------------|:-----------------------------|
| **Total Requests**        | 5,050                | 5,050                        |
| **Min Latency**           | 18 ms                | 1 ms                         |
| **Median (p50)**          | **3,820 ms**         | **16 ms**                    |
| **95th Percentile (p95)** | **6,804 ms**         | **18 ms**                    |
| **99th Percentile (p99)** | **7,574 ms**         | **46 ms**                    |
| **Max Latency**           | 8,374 ms             | 100 ms                       |
| **Requests < 800ms**      | 21.01%               | **100.00%**                  |

---

### Internal Service Metrics (Micrometer / Actuator)

* **`aml.transaction.evaluation.latency` (Pure Business Logic):**
    * **Mean Execution Time:** `20.8 ms`
    * **Max Execution Time:** `44.1 ms`
* **`spring.kafka.listener` (Single-threaded Consumer Processing):**
    * **Mean Processing Time:** `17.4 ms` (~48 TPS per thread)
    * **Peak Consumer Lag:** `4,500 messages`

---

## 3. Bottleneck Analysis

### 1. Synchronous REST Thread Starvation

When handling 200 RPS synchronously, each request blocks a Tomcat thread while performing IO and acquiring distributed
locks in Valkey/Redis. Once all 200 servlet threads were occupied, incoming requests queued at the TCP socket layer,
causing p95 latency to spike to **6,804 ms**.

### 2. Single Consumer Throughput Limits

With a core domain processing time of ~20.8 ms, a single consumer thread can process a maximum of:
$$\text{Throughput} = \frac{1000\text{ ms}}{20.8\text{ ms}} \approx 48\text{ TPS}$$

Because ingestion ran at 200 RPS, the consumer fell behind, accumulating a peak lag of 4,500 messages before draining
the queue post-test.

### 3. Outbox Poller Latency & Throughput Cap

The legacy `@Scheduled` outbox implementation suffered from two critical architectural limits:

* **Batch Size Limit:** Capped at 50 records per batch with a 1,000 ms delay = **50 TPS hard limit** (Target: 10,000
  TPS).
* **Latency Inflation:** Added up to **1,000 ms** of latency between transaction commit and event emission, breaking the
  `< 100 ms` SLA.

---

## 4. Architectural Decisions (ADR)

1. **Adopt Debezium CDC (Change Data Capture) for Outbox Pattern:**
    * **Action:** Replace `@Scheduled` SQL polling with Debezium listening to PostgreSQL WAL (Write-Ahead Logging).
    * **Impact:** Reduces outbox streaming delay from **~1000 ms to 5–10 ms** and scales outbox publishing beyond
      **30,000+ TPS** without database query overhead.

2. **Scale Kafka Consumer Concurrency:**
    * **Action:** Increase inbound topic partition count and configure `spring.kafka.listener.concurrency` (e.g., set to
      4+ instances/threads).
    * **Impact:** 4 concurrent threads will yield `4 * 48 TPS = 192+ TPS`, eliminating consumer lag under a 200 RPS
      load.

3. **Retain Async Event-Driven Paradigm for Ingestion:**
    * **Action:** Default to asynchronous Kafka evaluation for transaction processing.
    * **Impact:** Guarantees zero data loss (At-Least-Once delivery) while isolating client response times (p95 = 18 ms)
      from backend processing queues.