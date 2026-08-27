package io.github.anakidkin.aml.generator;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class AmlTestDataGenerator {

  public static Iterator<Map<String, Object>> createTransactionFeeder() {
    return Stream.generate(() -> {
      ThreadLocalRandom r = ThreadLocalRandom.current();
      String accountFrom = "ACC-" + (1000 + r.nextInt() * 9000);
      String accountTo = "ACC-" + (1000 + r.nextInt() * 9000);
      double amount = 10 + r.nextDouble() * 5000;
      String mccCode = String.format("%04d", r.nextInt() * 10000);
      boolean isP2p = Math.random() > 0.5;
      UUID txId = UUID.randomUUID();

      return Map.<String, Object>of(
          "txId", txId.toString(),
          "accountFrom", accountFrom,
          "accountTo", accountTo,
          "amount", amount,
          "currency", "EUR",
          "mccCode", mccCode,
          "isP2p", isP2p
      );
    }).iterator();
  }
}
