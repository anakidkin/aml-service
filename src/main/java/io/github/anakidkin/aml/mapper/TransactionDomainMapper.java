package io.github.anakidkin.aml.mapper;

import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.dto.InboundTransactionEvent;
import io.github.anakidkin.aml.dto.TransactionEvaluatedEvent;
import io.github.anakidkin.aml.entity.TransactionEntity;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    imports = {Instant.class, TransactionStatus.class})
public interface TransactionDomainMapper {

  // Domain -> JPA Entity
  @Mapping(target = "amount", source = "money.amount")
  @Mapping(target = "currency", source = "money.currency")
  @Mapping(target = "riskLevel", source = "riskAssessment.level")
  @Mapping(target = "riskScore", source = "riskAssessment.score")
  TransactionEntity toEntity(Transaction transaction);

  @Mapping(target = "id", source = "transactionId")
  @Mapping(target = "money.amount", source = "amount")
  @Mapping(target = "money.currency", source = "currency")
  @Mapping(target = "createdAt", source = "timestamp")
  @Mapping(target = "status", expression = "java(TransactionStatus.NEW)")
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "riskAssessment", ignore = true)
  Transaction toDomain(InboundTransactionEvent event);

  @Mapping(target = "transactionId", source = "tx.id")
  @Mapping(target = "amount", source = "tx.money.amount")
  @Mapping(target = "currency", source = "tx.money.currency")
  @Mapping(target = "evaluatedAt", expression = "java(Instant.now())")
  TransactionEvaluatedEvent toEvaluatedEvent(Transaction tx, List<RuleResult> ruleResults);
}
