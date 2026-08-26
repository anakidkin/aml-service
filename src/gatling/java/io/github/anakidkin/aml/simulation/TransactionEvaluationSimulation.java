package io.github.anakidkin.aml.simulation;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class TransactionEvaluationSimulation extends Simulation {

  // Read URL dynamically exposed by AbstractIntegrationTest or environment
  private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");

  private final HttpProtocolBuilder httpProtocol = http
      .baseUrl(baseUrl)
      .acceptHeader("application/json")
      .contentTypeHeader("application/json");

  // Dynamic feeder generating valid data according to TransactionRequest validation annotations
  private final Iterator<Map<String, Object>> feeder = Stream.generate(() -> Map.<String, Object>of(
      "accountFrom", "ACC-" + (1000 + (int) (Math.random() * 9000)),
      "accountTo", "ACC-" + (1000 + (int) (Math.random() * 9000)),
      "amount", 10 + (Math.random() * 5000),
      "currency", "EUR",
      "mccCode", String.format("%04d", (int) (Math.random() * 10000)), // Ensures valid 4-digit string
      "isP2p", Math.random() > 0.5
  )).iterator();

  private final ScenarioBuilder scn = scenario("AML Transaction Evaluation Load Test")
      .feed(feeder)
      .exec(
          http("Evaluate Rules Request")
              .post("/api/v1/transactions/evaluate")
              .body(StringBody("""
                  {
                      "accountFrom": "#{accountFrom}",
                      "accountTo": "#{accountTo}",
                      "amount": #{amount},
                      "currency": "#{currency}",
                      "mccCode": "#{mccCode}",
                      "isP2p": #{isP2p}
                  }
                  """))
              .check(status().is(200))
      );

  public TransactionEvaluationSimulation() {
    setUp(
        scn.injectOpen(
            nothingFor(Duration.ofSeconds(2)),
            rampUsersPerSec(10).to(200).during(Duration.ofSeconds(10)),
            constantUsersPerSec(200).during(Duration.ofSeconds(20))
        )
    ).protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile(95.0).lt(100),
            global().successfulRequests().percent().gt(99.0)
        );
  }
}