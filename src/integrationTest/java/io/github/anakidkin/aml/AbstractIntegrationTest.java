package io.github.anakidkin.aml;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.repository.CassandraAuditRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.repository.JpaTransactionRepository;
import io.github.anakidkin.aml.service.TransactionEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  static final Network network = Network.newNetwork();

  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine")
      .withNetwork(network)
      .withNetworkAliases("postgres")
      .withCommand("postgres", "-c", "wal_level=logical", "-c", "max_wal_senders=4", "-c", "max_replication_slots=4")
      .withReuse(true);

  static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"))
      .withNetwork(network)
      .withNetworkAliases("kafka")
      .withReuse(true);

  static final CassandraContainer cassandra = new CassandraContainer("cassandra:6.0")
      .withInitScript("cassandra/init-scripts/cassandra-tables.cql").withReuse(true);

  @SuppressWarnings("resource")
  static final GenericContainer<?> redis = new GenericContainer<>("valkey/valkey:9-alpine")
      .withExposedPorts(6379)
      .withCommand("valkey-server --requirepass testpassword")
      .withReuse(true);

  @SuppressWarnings("resource")
  static final GenericContainer<?> debezium = new GenericContainer<>("quay.io/debezium/connect:3.7")
      .withNetwork(network)
      .withExposedPorts(8083)
      .withEnv("BOOTSTRAP_SERVERS", "kafka:9092")
      .withEnv("GROUP_ID", "1")
      .withEnv("CONFIG_STORAGE_TOPIC", "connect_configs")
      .withEnv("OFFSET_STORAGE_TOPIC", "connect_offsets")
      .withEnv("STATUS_STORAGE_TOPIC", "connect_statuses")
      .withEnv("KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
      .withEnv("VALUE_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
      .dependsOn(postgres, kafka)
      .waitingFor(Wait.forHttp("/connectors").forStatusCode(200))
      .withReuse(true);

  static {
    // Start containers once for the entire JVM session
    postgres.start();
    kafka.start();
    cassandra.start();
    redis.start();
    debezium.start();

    registerDebeziumConnector();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

    registry.add("spring.cassandra.contact-points", () -> cassandra.getHost() + ":" + cassandra.getMappedPort(9042));
    registry.add("spring.cassandra.local-datacenter", cassandra::getLocalDatacenter);

    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    registry.add("spring.data.redis.password", () -> "testpassword");
  }

  private static void registerDebeziumConnector() {
    String connectorJson = """
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
        """.formatted(postgres.getUsername(), postgres.getPassword(), postgres.getDatabaseName());

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(java.net.URI.create("http://" + debezium.getHost() + ":" + debezium.getMappedPort(8083) + "/connectors"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(connectorJson))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 409) {
        throw new IllegalStateException("Failed to register Debezium connector: " + response.body());
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not initialize Debezium connector in Testcontainers", e);
    }
  }

  @Autowired
  protected CqlSession cqlSession;

  @Autowired
  protected KafkaTemplate<String, String> kafkaTemplate;

  @Autowired
  protected ObjectMapper objectMapper;

  @Value("${aml.kafka.topics.transaction-evaluated:aml.transaction-evaluated.v1}")
  protected String topicName;

  @Autowired
  protected CassandraHistoryRepository cassandraHistoryRepository;

  @Autowired
  protected CassandraAuditRepository cassandraAuditRepository;


  @Autowired
  protected TransactionEvaluationService transactionEvaluationService;

  @Autowired
  protected JpaOutboxRepository jpaOutboxRepository;

  @MockitoSpyBean
  @SuppressWarnings("rawtypes")
  protected KafkaTemplate kafkaTemplateSpy;

  @MockitoSpyBean
  protected JpaTransactionRepository jpaTransactionRepository;

}