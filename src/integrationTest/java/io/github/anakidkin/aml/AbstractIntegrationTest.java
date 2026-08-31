package io.github.anakidkin.aml;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.github.anakidkin.aml.repository.CassandraAuditRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.repository.JpaTransactionRepository;
import io.github.anakidkin.aml.service.TransactionEvaluationService;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  private static final boolean REUSE_CONTAINERS =
      loadProperty("testcontainer.reuse.enabled").map(Boolean::parseBoolean).orElse(false);

  private static final int KAFKA_HOST_PORT = findFreePort();

  private static int findFreePort() {
    try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
      return s.getLocalPort();
    } catch (java.io.IOException e) {
      throw new RuntimeException("Cannot find free port", e);
    }
  }

  static final Network network = Network.newNetwork();

  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withNetwork(network)
          .withNetworkAliases("postgres")
          .withCommand(
              "postgres",
              "-c",
              "wal_level=logical",
              "-c",
              "max_wal_senders=4",
              "-c",
              "max_replication_slots=4")
          .withReuse(REUSE_CONTAINERS)
          .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("PG"));

  @SuppressWarnings("resource")
  static final GenericContainer<?> kafka =
      new GenericContainer<>(DockerImageName.parse("apache/kafka:4.3.1"))
          .withNetwork(network)
          .withNetworkAliases("kafka")
          .withExposedPorts(9092)
          .withEnv("KAFKA_NODE_ID", "1")
          .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
          .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:29093")
          .withEnv(
              "KAFKA_LISTENERS",
              "PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093,PLAINTEXT_HOST://0.0.0.0:9092")
          .withEnv(
              "KAFKA_ADVERTISED_LISTENERS",
              "PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:" + KAFKA_HOST_PORT)
          .withEnv(
              "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
              "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT")
          .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
          .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
          .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
          .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
          .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
          .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.getHostConfig()
                      .withPortBindings(
                          new PortBinding(
                              Ports.Binding.bindPort(KAFKA_HOST_PORT), new ExposedPort(9092))))
          .withReuse(REUSE_CONTAINERS)
          .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("KAFKA"))
          .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));

  static final CassandraContainer cassandra =
      new CassandraContainer("cassandra:6.0")
          .withInitScript("cassandra/init-scripts/cassandra-tables.cql")
          .withReuse(REUSE_CONTAINERS)
          .withEnv("MAX_HEAP_SIZE", "512M")
          .withEnv("HEAP_NEWSIZE", "128M")
          .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("CASSANDRA"))
          .withEnv("CASSANDRA_AUTO_BOOTSTRAP", "false");

  @SuppressWarnings("resource")
  static final GenericContainer<?> redis =
      new GenericContainer<>("valkey/valkey:9-alpine")
          .withExposedPorts(6379)
          .withCommand("valkey-server --requirepass testpassword")
          .withReuse(REUSE_CONTAINERS)
          .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("REDIS"));

  @SuppressWarnings("resource")
  static final GenericContainer<?> debezium =
      new GenericContainer<>("quay.io/debezium/connect:3.7")
          .withNetwork(network)
          .withExposedPorts(8083)
          .withEnv("BOOTSTRAP_SERVERS", "kafka:29092")
          .withEnv("GROUP_ID", "1")
          .withEnv("CONFIG_STORAGE_TOPIC", "connect_configs")
          .withEnv("OFFSET_STORAGE_TOPIC", "connect_offsets")
          .withEnv("STATUS_STORAGE_TOPIC", "connect_statuses")
          .withEnv("KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
          .withEnv("VALUE_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
          .dependsOn(postgres, kafka)
          .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("DEBEZIUM"))
          .waitingFor(Wait.forHttp("/connectors").forStatusCode(200))
          .withReuse(REUSE_CONTAINERS);

  private static Optional<String> loadProperty(String flag) {
    try {
      YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
      yamlFactory.setResources(new ClassPathResource("application-test.yml"));
      return Optional.ofNullable(yamlFactory.getObject()).map(val -> val.getProperty(flag, null));
    } catch (Exception _) {
      return Optional.empty();
    }
  }

  static {
    Stream.of(postgres, kafka, cassandra, redis, debezium).forEach(Startable::start);
    registerDebeziumConnector();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:" + KAFKA_HOST_PORT);

    registry.add(
        "spring.cassandra.contact-points",
        () -> cassandra.getHost() + ":" + cassandra.getMappedPort(9042));
    registry.add("spring.cassandra.local-datacenter", cassandra::getLocalDatacenter);

    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    registry.add("spring.data.redis.password", () -> "testpassword");
  }

  private static void registerDebeziumConnector() {
    String connectorJson =
        """
        {
          "name": "aml-outbox-connector",
          "config": {
            "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
            "tasks.max": "1",
            "database.hostname": "postgres",
            "database.port": "5432",
            "database.user": "%s",
            "database.password": "%s",
            "database.dbname": "%s",
            "topic.prefix": "aml",
            "table.include.list": "public.outbox_events",
            "plugin.name": "pgoutput",
            "transforms": "outbox",
            "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
            "transforms.outbox.table.field.event.id": "id",
            "transforms.outbox.table.field.event.key": "aggregate_id",
            "transforms.outbox.table.field.event.payload": "payload",
            "transforms.outbox.route.by.field": "aggregate_type",
            "transforms.outbox.route.topic.replacement": "aml.${routedByValue}-evaluated.v1"
          }
        }
        """
            .formatted(postgres.getUsername(), postgres.getPassword(), postgres.getDatabaseName());

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  java.net.URI.create(
                      "http://"
                          + debezium.getHost()
                          + ":"
                          + debezium.getMappedPort(8083)
                          + "/connectors"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(connectorJson))
              .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200
          && response.statusCode() != 201
          && response.statusCode() != 409) {
        throw new IllegalStateException(
            "Failed to register Debezium connector: " + response.body());
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not initialize Debezium connector in Testcontainers", e);
    }
  }

  @Autowired protected CqlSession cqlSession;

  @Autowired protected KafkaTemplate<String, String> kafkaTemplate;

  @Autowired protected ObjectMapper objectMapper;

  @Value("${aml.kafka.topics.transaction-evaluated:aml.transaction-evaluated.v1}")
  protected String topicName;

  @Autowired protected CassandraHistoryRepository cassandraHistoryRepository;

  @Autowired protected CassandraAuditRepository cassandraAuditRepository;

  @Autowired protected TransactionEvaluationService transactionEvaluationService;

  @Autowired protected JpaOutboxRepository jpaOutboxRepository;

  @MockitoSpyBean
  @SuppressWarnings("rawtypes")
  protected KafkaTemplate kafkaTemplateSpy;

  @MockitoSpyBean protected JpaTransactionRepository jpaTransactionRepository;
}
