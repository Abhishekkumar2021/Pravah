package io.pravah.test.fixtures;

import io.pravah.common.domain.TenantId;
import net.datafaker.Faker;

/**
 * Test fixtures for tenant-related test data.
 */
public final class TenantFixtures {

    private static final Faker FAKER = new Faker();

    private TenantFixtures() {
        // Utility class
    }

    /**
     * Generate a random valid tenant ID.
     */
    public static TenantId randomTenantId() {
        String slug = FAKER.internet().slug().toLowerCase().replace("_", "-");
        // Ensure it meets the format requirements
        if (slug.length() < 3) {
            slug = slug + "-tenant";
        }
        if (slug.length() > 63) {
            slug = slug.substring(0, 63);
        }
        // Ensure ends with alphanumeric
        if (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1) + "x";
        }
        return TenantId.of(slug);
    }

    /**
     * Generate a well-known tenant ID for consistent testing.
     */
    public static TenantId testTenantId() {
        return TenantId.of("test-tenant");
    }

    /**
     * Generate an ACME Corp tenant ID (commonly used in examples).
     */
    public static TenantId acmeTenantId() {
        return TenantId.of("acme-corp");
    }
}
