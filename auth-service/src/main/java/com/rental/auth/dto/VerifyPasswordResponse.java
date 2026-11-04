package com.rental.auth.dto;

/**
 * {@code valid=false} is a normal business answer (wrong password / disabled account) and does not
 * reveal whether the account exists beyond what the caller already knows.
 */
public record VerifyPasswordResponse(boolean valid, String username) {
}
