package io.github.anakidkin.aml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Anti-Money Laundering (AML) transaction processing application.
 * Bootstraps Spring Boot context, enables auto-configuration, and initializes domain services.
 */
@EnableScheduling
@SpringBootApplication
public class AmlApplication {

  static void main(String[] args) {
    SpringApplication.run(AmlApplication.class, args);
  }
}
