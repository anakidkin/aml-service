package io.github.anakidkin.aml.mapper;

import io.github.anakidkin.aml.domain.Money;
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
    imports = {UUID.class, Instant.class, Money.class, TransactionStatus.class}
)
public interface TransactionApiMapper {

  @Mapping(target = "id", expression = "java(UUID.randomUUID())")
  @Mapping(target = "status", expression = "java(TransactionStatus.NEW)")
  @Mapping(target = "riskAssessment", ignore = true) // Оставляем null, так как в конструкторе НЕТ проверки на riskAssessment
  @Mapping(target = "createdAt", expression = "java(Instant.now())")
  @Mapping(target = "updatedAt", expression = "java(Instant.now())")
  @Mapping(target = "money", expression = "java(new Money(request.amount(), request.currency()))")
  Transaction toDomain(TransactionRequest request);

  @Mapping(target = "amount", source = "money.amount")
  @Mapping(target = "currency", source = "money.currency")
  @Mapping(target = "riskLevel", expression = "java(transaction.riskAssessment() != null && transaction.riskAssessment().level() != null ? transaction.riskAssessment().level().name() : null)")
  @Mapping(target = "riskScore", expression = "java(transaction.riskAssessment() != null ? transaction.riskAssessment().score() : null)")
  TransactionResponse toResponse(Transaction transaction);
}