package com.rental.auth.security;

import java.util.List;

/**
 * Ket qua parse access token hop le (claims theo docs muc 7.1).
 *
 * @param accountId claim sub = account id
 * @param userType claim typ (CUSTOMER, STAFF, ADMIN)
 * @param roles claim roles
 * @param tokenId claim jti
 */
public record JwtClaims(Long accountId, String userType, List<String> roles, String tokenId) {
}
