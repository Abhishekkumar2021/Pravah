package io.pravah.playground.graphql.model;

/**
 * Authentication context extracted from the request.
 * In production, this comes from the JWT token.
 * For the playground, we simulate it via headers.
 */
public record AuthContext(
        String userId,
        String tenantId,
        Role role
) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
