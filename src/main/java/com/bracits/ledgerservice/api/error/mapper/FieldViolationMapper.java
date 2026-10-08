package com.bracits.ledgerservice.api.error.mapper;

import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import java.util.List;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps the field-level errors of Spring MVC's validation exceptions
 * ({@link MethodArgumentNotValidException} for bodies, {@link HandlerMethodValidationException} for
 * parameters) to the {@code errors} list of a problem. Any other exception has none.
 */
public interface FieldViolationMapper {

  /**
   * The field violations of {@code ex}, in Spring's order; empty when it carries none.
   */
  List<FieldViolation> violationsOf(Exception ex);
}
