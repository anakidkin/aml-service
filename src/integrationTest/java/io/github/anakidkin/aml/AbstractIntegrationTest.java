package io.github.anakidkin.aml;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.anakidkin.aml.repository.CassandraAuditRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.repository.JpaOutboxRepository;
import io.github.anakidkin.aml.repository.JpaTransactionRepository;
import io.github.anakidkin.aml.scheduler.OutboxEventRelay;
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
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine").withReuse(true);
  static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1")).withReuse(true);
  static final CassandraContainer cassandra = new CassandraContainer("cassandra:6.0")
      .withInitScript("cassandra/init-scripts/cassandra-tables.cql").withReuse(true);

  static {
    // Start containers once for the entire JVM session
    postgres.start();
    kafka.start();
    cassandra.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);

    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

    registry.add("spring.cassandra.contact-points", () -> cassandra.getHost() + ":" + cassandra.getMappedPort(9042));
    registry.add("spring.cassandra.local-datacenter", cassandra::getLocalDatacenter);
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

  @Autowired
  protected OutboxEventRelay outboxEventRelay;

  @MockitoSpyBean
  @SuppressWarnings("rawtypes")
  protected KafkaTemplate kafkaTemplateSpy;

  @MockitoSpyBean
  protected JpaTransactionRepository jpaTransactionRepository;

}