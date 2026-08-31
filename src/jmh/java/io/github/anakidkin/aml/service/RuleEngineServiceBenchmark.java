package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.config.AmlRulesConfig;
import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Money;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.rules.AmlRule;
import io.github.anakidkin.aml.service.impl.RuleEngineServiceImpl;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class RuleEngineServiceBenchmark {

  private RuleEngineService ruleEngineService;
  private Transaction[] transactions;
  private AccountContext[] contexts;

  private final AtomicInteger index = new AtomicInteger(0);
  private static final int DATASET_SIZE = 1024;
  private static final int MASK = DATASET_SIZE - 1;

  @Setup
  public void setup() {
    AmlRulesConfig config = new AmlRulesConfig();
    List<AmlRule> rules = config.amlRules();

    this.ruleEngineService = new RuleEngineServiceImpl(rules);

    this.transactions = new Transaction[DATASET_SIZE];
    this.contexts = new AccountContext[DATASET_SIZE];

    Random rnd = new Random(42);

    for (int i = 0; i < DATASET_SIZE; i++) {
      double amount = 100.0 + (rnd.nextDouble() * 100000.0);
      boolean isP2p = rnd.nextBoolean();

      this.transactions[i] = new Transaction(
          UUID.randomUUID(), "ACC_" + i, "ACC_" + (i + 1),
          new Money(BigDecimal.valueOf(amount), "USD"),
          isP2p ? "6012" : "5411", isP2p, TransactionStatus.PENDING,
          null, Instant.now(), Instant.now()
      );

      this.contexts[i] = new AccountContext(
          amount * (0.5 + rnd.nextDouble()),
          rnd.nextInt(50),
          rnd.nextInt(20),
          rnd.nextDouble(),
          rnd.nextBoolean()
      );
    }
  }

  @Benchmark
  public void evaluateRulesViaService(Blackhole bh) {
    int idx = index.getAndIncrement() & MASK;
    Transaction tx = transactions[idx];
    AccountContext context = contexts[idx];

    List<RuleResult> results = ruleEngineService.evaluate(tx, context);
    bh.consume(results);
  }
}