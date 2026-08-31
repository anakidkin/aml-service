package io.github.anakidkin.aml.mapper;

import io.gatling.javaapi.core.Session;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AmlPayloadMapper {

  public static String toRestRequestJson(Session session) {
    return String.format(
        Locale.US,
        """
            {
                "accountFrom": "%s",
                "accountTo": "%s",
                "amount": %.2f,
                "currency": "%s",
                "mccCode": "%s",
                "isP2p": %b
            }
            """,
        session.getString("accountFrom"),
        session.getString("accountTo"),
        session.getDouble("amount"),
        session.getString("currency"),
        session.getString("mccCode"),
        session.getBoolean("isP2p"));
  }

  public static String toInboundKafkaJson(Session session) {
    return String.format(
        Locale.US,
        """
            {
                "transactionId": "%s",
                "accountFrom": "%s",
                "accountTo": "%s",
                "amount": %.2f,
                "currency": "%s",
                "mccCode": "%s",
                "isP2p": %b,
                "timestamp": "%s"
            }
            """,
        UUID.randomUUID(),
        session.getString("accountFrom"),
        session.getString("accountTo"),
        session.getDouble("amount"),
        session.getString("currency"),
        session.getString("mccCode"),
        session.getBoolean("isP2p"),
        Instant.now().toString());
  }
}
