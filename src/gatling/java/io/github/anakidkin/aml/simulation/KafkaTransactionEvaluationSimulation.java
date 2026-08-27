package io.github.anakidkin.aml.simulation;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.github.anakidkin.aml.generator.AmlTestDataGenerator;
import io.github.anakidkin.aml.mapper.AmlPayloadMapper;
import org.galaxio.gatling.kafka.javaapi.protocol.KafkaProtocolBuilder;

import java.time.Duration;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static org.galaxio.gatling.kafka.javaapi.KafkaDsl.kafka;

public class KafkaTransactionEvaluationSimulation extends Simulation {

  private final String kafkaBrokers = System.getProperty("kafkaBrokers", "localhost:9092");
  private final String topic = System.getProperty("kafkaTopic", "aml.transactions-inbound.v1");

  private final KafkaProtocolBuilder kafkaProtocol = kafka()
      .properties(Map.of(
          "bootstrap.servers", kafkaBrokers,
          "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
          "value.serializer", "org.apache.kafka.common.serialization.StringSerializer",
          "acks", "1"
      ));

  private final ScenarioBuilder scn = scenario("AML Kafka Listener Load Test")
      .feed(AmlTestDataGenerator.createTransactionFeeder())
      .exec(session -> session.set("payloadJson", AmlPayloadMapper.toInboundKafkaJson(session)))
      .exec(
          kafka("Send Inbound Transaction Event")
              .topic(topic)
              .send("#{accountFrom}", "#{payloadJson}")
      );

  public KafkaTransactionEvaluationSimulation() {
    setUp(
        scn.injectOpen(
            nothingFor(Duration.ofSeconds(2)),
            rampUsersPerSec(10).to(200).during(Duration.ofSeconds(10)),
            constantUsersPerSec(200).during(Duration.ofSeconds(20))
        )
    ).protocols(kafkaProtocol)
        .assertions(  // Useless as it only measures writes to Kafka, not processing
            global().responseTime().percentile(95.0).lt(100),
            global().successfulRequests().percent().gt(99.0)
        );
  }
}