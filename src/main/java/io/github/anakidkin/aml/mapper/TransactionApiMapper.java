package io.github.anakidkin.aml.mapper;

import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.domain.TransactionStatus;
import io.github.anakidkin.aml.dto.TransactionRequest;
import io.github.anakidkin.aml.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.time.Instant;
import java.util.UUID;

/**
 * MapStruct / Component mapper responsible for conversions between API DTOs
 * and domain model entities.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    imports = {UUID.class, Instant.class, TransactionStatus.class}
)
public interface TransactionApiMapper {

  @Mapping(target = "id", expression = "java(UUID.randomUUID())")
  @Mapping(target = "status", expression = "java(TransactionStatus.NEW)")
  @Mapping(target = "createdAt", expression = "java(Instant.now())")
  @Mapping(target = "money.amount", source = "amount")
  @Mapping(target = "money.currency", source = "currency")
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "riskAssessment", ignore = true)
  Transaction toDomain(TransactionRequest request);

  @Mapping(target = "amount", source = "money.amount")
  @Mapping(target = "currency", source = "money.currency")
  @Mapping(target = "riskLevel", expression = "java(transaction.riskAssessment() != null && transaction.riskAssessment().level() != null ? transaction.riskAssessment().level().name() : null)")
  @Mapping(target = "riskScore", expression = "java(transaction.riskAssessment() != null ? transaction.riskAssessment().score() : null)")
  TransactionResponse toResponse(Transaction transaction);
}