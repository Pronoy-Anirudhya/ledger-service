package com.bracits.ledgerservice.api.error.mapper.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import com.bracits.ledgerservice.api.error.mapper.FieldViolationMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class FieldViolationMapperImplTest {

  private static final String OBJECT_NAME = "postingRequest";

  private final FieldViolationMapper mapper = new FieldViolationMapperImpl();

  @Test
  void bodyViolationsListFieldErrorsThenGlobalErrors() {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(null, OBJECT_NAME);
    bindingResult.addError(new ObjectError(OBJECT_NAME, "legs must balance"));
    bindingResult.addError(new FieldError(OBJECT_NAME, "legs[0].amount", "must be positive"));
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    when(ex.getBindingResult()).thenReturn(bindingResult);

    List<FieldViolation> violations = mapper.violationsOf(ex);

    assertThat(violations)
        .containsExactly(
            new FieldViolation("legs[0].amount", "must be positive"),
            new FieldViolation(OBJECT_NAME, "legs must balance"));
  }

  @Test
  void parameterViolationsUseTheParameterName() {
    MethodParameter parameter = mock(MethodParameter.class);
    when(parameter.getParameterName()).thenReturn("legs");
    ParameterValidationResult result = mock(ParameterValidationResult.class);
    when(result.getMethodParameter()).thenReturn(parameter);
    when(result.getResolvableErrors())
        .thenReturn(
            List.of(new DefaultMessageSourceResolvable(new String[]{"Min"}, "must be >= 1")));
    HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
    when(ex.getParameterValidationResults()).thenReturn(List.of(result));

    List<FieldViolation> violations = mapper.violationsOf(ex);

    assertThat(violations).containsExactly(new FieldViolation("legs", "must be >= 1"));
  }

  @Test
  void otherExceptionsHaveNoViolations() {
    List<FieldViolation> violations = mapper.violationsOf(new IllegalStateException("boom"));

    assertThat(violations).isEmpty();
  }
}
