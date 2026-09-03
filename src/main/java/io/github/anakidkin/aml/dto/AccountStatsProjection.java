package io.github.anakidkin.aml.dto;

import java.math.BigDecimal;

public interface AccountStatsProjection {
  Boolean getIsP2p();

  Long getTxCount();

  BigDecimal getTotalAmount();
}
