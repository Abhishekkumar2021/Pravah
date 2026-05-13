/**
 * libs:test-support
 *
 * Shared test utilities, fixtures, and Testcontainers configuration.
 * This module provides reusable testing infrastructure across services.
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Test Support - Shared test utilities and fixtures"

dependencies {
    // Common module
    api(project(":libs:common"))

    // Spring Boot Test
    api("org.springframework.boot:spring-boot-starter-test:3.5.14") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

    // Testcontainers - use explicit versions since BOM resolution is inconsistent in library modules
    api("org.testcontainers:junit-jupiter:1.19.8")
    api("org.testcontainers:postgresql:1.19.8")
    api("org.testcontainers:kafka:1.19.8")

    // Awaitility for async testing
    api("org.awaitility:awaitility:4.2.1")

    // Faker for test data generation
    api("net.datafaker:datafaker:2.5.4")
}
