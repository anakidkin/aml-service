package io.github.anakidkin.aml.simulation;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.github.anakidkin.aml.generator.AmlTestDataGenerator;
import io.github.anakidkin.aml.mapper.AmlPayloadMapper;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class RestTransactionEvaluationSimulation extends Simulation {

  // Read URL dynamically exposed by AbstractIntegrationTest or environment
  private final String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");

  private final HttpProtocolBuilder httpProtocol = http
      .baseUrl(baseUrl)
      .acceptHeader("application/json")
      .contentTypeHeader("application/json");

  private final ScenarioBuilder scn = scenario("AML Transaction Evaluation Load Test")
      .feed(AmlTestDataGenerator.createTransactionFeeder())
      .exec(
          http("Evaluate Rules Request")
              .post("/api/v1/transactions/evaluate")
              .body(StringBody(AmlPayloadMapper::toRestRequestJson))
              .asJson()
              .check(status().is(200))
      );

  public RestTransactionEvaluationSimulation() {
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