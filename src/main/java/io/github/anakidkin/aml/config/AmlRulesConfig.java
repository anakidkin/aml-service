package io.github.anakidkin.aml.config;

import io.github.anakidkin.aml.rules.AmlRule;
import io.github.anakidkin.aml.rules.impl.CounterpartyCountRule;
import io.github.anakidkin.aml.rules.impl.DailyVolumeLimitRule;
import io.github.anakidkin.aml.rules.impl.DormantAccountSpikeRule;
import io.github.anakidkin.aml.rules.impl.P2pUtilizationRatioRule;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmlRulesConfig {

  @Bean
  public List<AmlRule> amlRules() {
    return List.of(
        new CounterpartyCountRule(),
        new DailyVolumeLimitRule(),
        new DormantAccountSpikeRule(),
        new P2pUtilizationRatioRule());
  }
}
