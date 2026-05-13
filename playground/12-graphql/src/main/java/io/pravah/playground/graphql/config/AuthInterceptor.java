package io.pravah.playground.graphql.config;

import io.pravah.playground.graphql.model.AuthContext;
import io.pravah.playground.graphql.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Intercepts GraphQL requests to extract authentication context.
 * 
 * In production Pravah:
 * - Extracts JWT from Authorization header
 * - Validates the token
 * - Extracts user_id, tenant_id, role from claims
 * 
 * For the playground, we simulate via HTTP headers:
 * - X-User-ID: user id
 * - X-Tenant-ID: tenant id  
 * - X-Role: USER or ADMIN
 */
@Component
public class AuthInterceptor implements WebGraphQlInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        // Extract auth headers (simulating JWT extraction)
        String userId = request.getHeaders().getFirst("X-User-ID");
        String tenantId = request.getHeaders().getFirst("X-Tenant-ID");
        String roleHeader = request.getHeaders().getFirst("X-Role");

        // Default values for playground
        if (userId == null) userId = "anonymous";
        if (tenantId == null) tenantId = "tenant-acme";
        Role role = "ADMIN".equalsIgnoreCase(roleHeader) ? Role.ADMIN : Role.USER;

        AuthContext authContext = new AuthContext(userId, tenantId, role);
        log.debug("Auth context: user={}, tenant={}, role={}", userId, tenantId, role);

        // Add auth context to GraphQL context (accessible in resolvers)
        request.configureExecutionInput((executionInput, builder) ->
                builder.graphQLContext(ctx -> ctx.put("authContext", authContext)).build());

        return chain.next(request);
    }
}
