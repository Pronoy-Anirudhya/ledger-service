package com.bracits.ledgerservice.api.error.mapper.impl;

import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import com.bracits.ledgerservice.api.error.mapper.FieldViolationMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * {@link FieldViolationMapper} implementation.
 */
@Component
public final class FieldViolationMapperImpl implements FieldViolationMapper {

  @Override
  public List<FieldViolation> violationsOf(Exception ex) {
    List<FieldViolation> violations = new ArrayList<>();

    switch (ex) {
      case MethodArgumentNotValidException notValid -> addBodyViolations(notValid, violations);
      case HandlerMethodValidationException methodValidation ->
          addParameterViolations(methodValidation, violations);
      default -> {
        // no field-level details
      }
    }

    return violations;
  }

  private static void addBodyViolations(
      MethodArgumentNotValidException notValid, List<FieldViolation> violations) {
    for (FieldError error : notValid.getBindingResult().getFieldErrors()) {
      violations.add(new FieldViolation(error.getField(), error.getDefaultMessage()));
    }

    for (ObjectError error : notValid.getBindingResult().getGlobalErrors()) {
      violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage()));
    }
  }

  private static void addParameterViolations(
      HandlerMethodValidationException methodValidation, List<FieldViolation> violations) {
    for (ParameterValidationResult result : methodValidation.getParameterValidationResults()) {
      String field = result.getMethodParameter().getParameterName();

      for (MessageSourceResolvable error : result.getResolvableErrors()) {
        violations.add(new FieldViolation(field, error.getDefaultMessage()));
      }
    }
  }
}
