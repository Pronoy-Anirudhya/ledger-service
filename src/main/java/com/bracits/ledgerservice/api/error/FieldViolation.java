package com.bracits.ledgerservice.api.error;

/**
 * One entry of the {@code errors} member of a 400 {@code VALIDATION_FAILED} problem.
 *
 * @param field the offending field or parameter, e.g. {@code legs[0].amount}
 * @param message why it is invalid
 */
public record FieldViolation(String field, String message) {}
