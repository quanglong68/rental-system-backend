package com.rental.common.error;

/**
 * Chi tiet loi theo tung field, nam trong ErrorResponse.details (docs muc 8).
 *
 * @param field ten field loi, vi du customerId
 * @param message mo ta loi cua field do
 */
public record FieldViolation(String field, String message) {
}
